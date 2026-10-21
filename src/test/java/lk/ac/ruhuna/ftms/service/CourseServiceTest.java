package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Dependency-free runner, matching the existing test suite; no database writes. */
public final class CourseServiceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var dao = new RecordingDAO();
        var service = new CourseService(dao, CourseService::isCurrentUserAdmin);
        try {
            SessionManager.logout();
            deniedOperations(service, dao);
            for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
                SessionManager.startSession(user(role, true));
                deniedOperations(service, dao);
            }
            SessionManager.logout();
            expect(IllegalArgumentException.class, () -> SessionManager.startSession(user("ADMIN", false)));
            check(!CourseService.isCurrentUserAdmin(), "inactive admin cannot establish an authorized session");
            deniedOperations(service, dao);

            SessionManager.startSession(user("ADMIN", true));
            check(service.getAllCourses().size() == 1, "list courses");
            service.searchCourses("  ICT  ");
            check("ICT".equals(dao.keyword), "trim search");
            int searches = dao.searches;
            service.searchCourses("  "); service.searchCourses(null);
            check(dao.searches == searches, "blank search lists all");
            Course valid = course();
            valid.setCourseCode(" ICT101 "); valid.setCourseName(" Computing ");
            valid.setCourseType(" elective "); valid.setActive(false);
            check(service.createCourse(valid) == 42, "create returns generated ID");
            check(valid.getCourseCode().equals("ICT101") && valid.getCourseName().equals("Computing"), "trim code and name");
            check(valid.getCourseType().equals("ELECTIVE") && !dao.saved.isActive(), "type normalization and inactive status");
            valid.setCourseId(42); valid.setActive(true);
            check(service.updateCourse(valid) && dao.saved.isActive(), "update active status");
            check(service.deleteCourse(42), "delete valid selection");

            List<Consumer<Course>> invalid = List.of(
                    c -> c.setCourseCode(null), c -> c.setCourseCode(" "), c -> c.setCourseCode("X".repeat(31)),
                    c -> c.setCourseName(null), c -> c.setCourseName(" "), c -> c.setCourseName("X".repeat(181)),
                    c -> c.setCredits(null), c -> c.setCredits(new BigDecimal("0")),
                    c -> c.setCredits(new BigDecimal("-1")), c -> c.setCredits(new BigDecimal("1000")),
                    c -> c.setCredits(new BigDecimal("1.01")), c -> c.setDepartmentId(0),
                    c -> c.setDepartmentId(-1), c -> c.setCourseType(null), c -> c.setCourseType("INVALID"));
            for (var edit : invalid) {
                Course bad = course(); bad.setCourseId(42); edit.accept(bad);
                int calls = dao.calls;
                expect(IllegalArgumentException.class, () -> service.createCourse(bad));
                expect(IllegalArgumentException.class, () -> service.updateCourse(bad));
                check(dao.calls == calls, "invalid input rejected before DAO");
            }
            expect(IllegalArgumentException.class, () -> service.createCourse(null));
            expect(IllegalArgumentException.class, () -> service.updateCourse(null));
            expect(IllegalArgumentException.class, () -> service.updateCourse(course()));
            expect(IllegalArgumentException.class, () -> service.deleteCourse(0));
            for (String credits : List.of("0.1", "999.9", "3.00")) {
                Course boundary = course(); boundary.setCredits(new BigDecimal(credits));
                check(service.createCourse(boundary) == 42, "valid credit boundary " + credits);
            }
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                Course elective = course(); elective.setCourseType("elective");
                service.createCourse(elective);
                check(elective.getCourseType().equals("ELECTIVE"), "type normalization independent of locale");
            } finally { Locale.setDefault(original); }
            var denied = new CourseService(dao, () -> false);
            deniedOperations(denied, dao);
            SessionManager.logout();
            deniedOperations(service, dao);
            SessionManager.startSession(user("STUDENT", true));
            deniedOperations(service, dao);
            System.out.println("Course service checks passed: " + checks);
        } finally { SessionManager.logout(); }
    }

    private static void deniedOperations(CourseService service, RecordingDAO dao) throws Exception {
        int calls = dao.calls;
        expect(SecurityException.class, service::getAllCourses);
        expect(SecurityException.class, () -> service.searchCourses("ICT"));
        expect(SecurityException.class, () -> service.createCourse(course()));
        expect(SecurityException.class, () -> service.updateCourse(course()));
        expect(SecurityException.class, () -> service.deleteCourse(42));
        check(calls == dao.calls, "unauthorized operations never reach DAO");
    }

    private static Course course() {
        Course c = new Course(); c.setCourseCode("ICT101"); c.setCourseName("Computing");
        c.setCredits(new BigDecimal("3.0")); c.setDepartmentId(1); c.setCourseType("CORE"); c.setActive(true);
        return c;
    }

    private static User user(String role, boolean active) { return new User(1, "fixture", "Fixture", "", role, active); }
    private interface Work { void run() throws Exception; }
    private static void expect(Class<? extends Exception> type, Work work) throws Exception {
        try { work.run(); } catch (Exception error) { if (type.isInstance(error)) { checks++; return; } throw error; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); checks++; }

    private static final class RecordingDAO extends CourseDAO {
        int calls, searches; String keyword; Course saved;
        @Override public List<Course> findAll() { calls++; return List.of(course()); }
        @Override public List<Course> search(String text) { calls++; searches++; keyword = text; return List.of(course()); }
        @Override public int insert(Course course) { calls++; saved = course; return 42; }
        @Override public boolean update(Course course) { calls++; saved = course; return true; }
        @Override public boolean delete(int id) { calls++; return true; }
    }
}
