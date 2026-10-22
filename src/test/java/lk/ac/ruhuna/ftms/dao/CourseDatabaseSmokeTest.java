package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit live runner: rollback constraint checks, then commit/reload/delete one unique test course. */
public final class CourseDatabaseSmokeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--safe-write")) {
            throw new IllegalArgumentException("Use --safe-write to test a unique temporary course and remove it afterwards.");
        }
        var admin = new UserDAO().getAllUsers().stream()
                .filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow();
        SessionManager.startSession(admin);
        try (Connection connection = DBConnection.getConnection()) {
            List<String> original = snapshot(connection, "courses");
            List<String> offerings = snapshot(connection, "course_offerings");
            int department = scalar(connection, "SELECT MIN(department_id) FROM departments");
            int referenced = scalar(connection, "SELECT MIN(course_id) FROM course_offerings");
            check(referenced > 0 && department > 0, "existing department and referenced course available");
            // Never attempt deletion against a database with cascading course references.
            check(scalar(connection, "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                    + "WHERE CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='courses' "
                    + "AND DELETE_RULE NOT IN ('RESTRICT','NO ACTION')") == 0, "course references forbid cascading deletes");
            connection.setAutoCommit(false);
            try {
                Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                            if (method.getName().equals("close")) return null;
                            try { return method.invoke(connection, arguments); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                        });
                var dao = new CourseDAO() { @Override protected Connection openConnection() { return borrowed; } };
                var service = new CourseService(dao, CourseService::isCurrentUserAdmin);
                check(service.getAllCourses().size() == original.size(), "existing MySQL courses load");
                Course course = fixture(department);
                int id = service.createCourse(course);
                check(id > 0 && id == course.getCourseId(), "generated ID returned");
                check(service.searchCourses(course.getCourseCode()).size() == 1, "search code");
                check(service.searchCourses(course.getCourseName()).size() == 1, "search name");
                expectSql(1062, () -> service.createCourse(course));
                Course other = fixture(department); service.createCourse(other);
                other.setCourseCode(course.getCourseCode());
                expectSql(1062, () -> service.updateCourse(other));
                course.setDepartmentId(Integer.MAX_VALUE);
                expectSql(1452, () -> service.updateCourse(course));
                Course invalidDepartment = fixture(Integer.MAX_VALUE);
                expectSql(1452, () -> service.createCourse(invalidDepartment));
                course.setDepartmentId(department);
                course.setCourseName("Edited " + course.getCourseCode());
                course.setCredits(new BigDecimal("4.5")); course.setCourseType("OPTIONAL"); course.setActive(false);
                check(service.updateCourse(course), "update MySQL course");
                Course loaded = service.searchCourses(course.getCourseCode()).getFirst();
                check(loaded.getCredits().compareTo(new BigDecimal("4.5")) == 0 && !loaded.isActive()
                        && loaded.getCourseType().equals("OPTIONAL") && loaded.getCourseName().equals(course.getCourseName()), "all edited fields round trip");
                check(service.deleteCourse(id), "delete unreferenced course");
                check(service.searchCourses(course.getCourseCode()).isEmpty(), "deleted row absent");
                expectSql(1451, () -> service.deleteCourse(referenced));
                check(offerings.equals(snapshot(connection, "course_offerings")), "dependency rejection preserves academic records");
            } finally { connection.rollback(); connection.setAutoCommit(true); }
            check(original.equals(snapshot(connection, "courses")), "rollback restores original courses");

            var committed = new CourseService(new CourseDAO(), CourseService::isCurrentUserAdmin);
            Course temporary = fixture(department);
            int temporaryId = 0;
            try {
                temporaryId = committed.createCourse(temporary);
                check(committed.searchCourses(temporary.getCourseCode()).size() == 1, "committed insert visible on a new connection");
                temporary.setCourseName("Persisted " + temporary.getCourseCode()); temporary.setActive(false);
                check(committed.updateCourse(temporary), "committed update");
                Course loaded = committed.searchCourses(temporary.getCourseCode()).getFirst();
                check(!loaded.isActive() && loaded.getCourseName().equals(temporary.getCourseName()), "committed update survives connection close");
            } finally {
                if (temporaryId > 0) check(committed.deleteCourse(temporaryId), "temporary committed course removed");
            }
            check(original.equals(snapshot(connection, "courses")), "all original courses unchanged after committed fixture cleanup");
            check(offerings.equals(snapshot(connection, "course_offerings")), "all offerings unchanged");
            System.out.println("Course MySQL checks passed: " + checks + "; original rows unchanged; temporary course removed.");
        } finally { SessionManager.logout(); }
    }

    private static Course fixture(int department) {
        String code = "CMTEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Course c = new Course(); c.setCourseCode(code); c.setCourseName("Course smoke " + code);
        c.setDepartmentId(department); c.setCredits(new BigDecimal("3.0")); c.setCourseType("CORE"); c.setActive(true);
        return c;
    }
    private static int scalar(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rs = s.executeQuery(sql)) { rs.next(); return rs.getInt(1); }
    }
    private static List<String> snapshot(Connection c, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var s = c.createStatement(); var rs = s.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
            while (rs.next()) {
                List<String> cells = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) cells.add(rs.getString(i));
                rows.add(cells.toString());
            }
        }
        return rows;
    }
    private interface Work { void run() throws Exception; }
    private static void expectSql(int code, Work work) throws Exception {
        try { work.run(); } catch (SQLException e) { check(e.getErrorCode() == code, "MySQL error " + code); return; }
        throw new AssertionError("Expected MySQL error " + code);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); checks++; }
}
