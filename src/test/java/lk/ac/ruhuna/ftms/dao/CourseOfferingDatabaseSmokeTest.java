package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.*;
import java.util.*;

/** Explicit live MySQL runner. Creates only unique offerings, then removes its own rows. */
public final class CourseOfferingDatabaseSmokeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (!Arrays.equals(args, new String[]{"--safe-write"})) {
            throw new IllegalArgumentException("Pass --safe-write to create and clean up unique offering fixtures.");
        }
        var admin = new UserDAO().getAllUsers().stream()
                .filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow();
        SessionManager.startSession(admin);
        var service = new CourseOfferingService(new CourseOfferingDAO(), CourseService::isCurrentUserAdmin);
        try (Connection connection = DBConnection.getConnection()) {
            Map<String, List<String>> original = snapshot(connection);
            Set<String> expectedReferences = Set.of("assessments", "lecturer_course_assignments", "attendance_sessions",
                    "enrollments", "course_materials", "medicals", "timetable_entries");
            Set<String> references = new HashSet<>();
            try (var keys = connection.getMetaData().getExportedKeys(connection.getCatalog(), null, "course_offerings")) {
                while (keys.next()) references.add(keys.getString("FKTABLE_NAME"));
            }
            check(references.equals(expectedReferences), "delete guard covers every live referencing table");
            var courses = service.getActiveCourses(); var semesters = service.getSemesters();
            check(!courses.isEmpty() && !semesters.isEmpty(), "actual active courses and semesters loaded");
            check(!service.getAllOfferings().isEmpty(), "existing offerings loaded");
            var first = fixture(courses.getFirst().courseId(), semesters.getFirst().semesterId());
            var second = fixture(first.getCourseId(), first.getSemesterId());
            try {
                check(service.saveOffering(first) && first.getOfferingId() > 0, "create returns generated identity");
                check(service.searchOfferings(first.getOfferingGroup()).size() == 1, "committed insert visible on new connection");
                var duplicate = fixture(first.getCourseId(), first.getSemesterId()); duplicate.setOfferingGroup(first.getOfferingGroup());
                expectSql(1062, () -> service.saveOffering(duplicate));
                check(service.saveOffering(second), "second unique fixture created");
                String secondGroup = second.getOfferingGroup(); second.setOfferingGroup(first.getOfferingGroup());
                expectSql(1062, () -> service.saveOffering(second)); second.setOfferingGroup(secondGroup);
                int courseId = first.getCourseId(); first.setCourseId(Integer.MAX_VALUE);
                expectSql(1452, () -> service.saveOffering(first)); first.setCourseId(courseId);
                int semesterId = first.getSemesterId(); first.setSemesterId(Integer.MAX_VALUE);
                expectSql(1452, () -> service.saveOffering(first)); first.setSemesterId(semesterId);
                first.setOfferingGroup(first.getOfferingGroup() + "E");
                for (String status : List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED")) {
                    first.setStatus(status); check(service.saveOffering(first), "edit " + status);
                    var loaded = service.searchOfferings(first.getOfferingGroup()).getFirst();
                    check(loaded.getStatus().equals(status) && loaded.getOfferingId() == first.getOfferingId(), "persisted edit " + status);
                }
                Set<Integer> linked = new HashSet<>();
                for (String table : references) {
                    try (var s = connection.createStatement(); var rows = s.executeQuery("SELECT DISTINCT offering_id FROM " + table + " WHERE offering_id IS NOT NULL")) {
                        while (rows.next()) linked.add(rows.getInt(1));
                    }
                }
                check(!linked.isEmpty(), "linked offerings available for deletion protection");
                for (int id : linked) expectSql(1451, () -> service.deleteOffering(id));
                check(service.deleteOffering(first.getOfferingId()), "unlinked fixture deleted");
                check(service.searchOfferings(first.getOfferingGroup()).isEmpty(), "deletion persists");
                check(!service.deleteOffering(first.getOfferingId()), "missing offering delete returns false");
            } finally {
                if (first.getOfferingId() > 0) service.deleteOffering(first.getOfferingId());
                if (second.getOfferingId() > 0) service.deleteOffering(second.getOfferingId());
            }
            check(original.equals(snapshot(connection)), "all original database rows unchanged, including linked academic records");
            System.out.println("Course Offering MySQL checks passed: " + checks + "; fixtures removed; all original table rows unchanged.");
        } finally { SessionManager.logout(); }
    }

    private static CourseOffering fixture(int course, int semester) {
        CourseOffering o = new CourseOffering(); o.setCourseId(course); o.setSemesterId(semester);
        o.setOfferingGroup("COT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        o.setStatus("PLANNED"); return o;
    }

    private static Map<String, List<String>> snapshot(Connection c) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")) {
            while (r.next()) tables.add(r.getString(1));
        }
        Map<String, List<String>> result = new TreeMap<>();
        for (String table : tables) {
            List<String> rows = new ArrayList<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM `" + table.replace("`", "``") + "`")) {
                while (r.next()) {
                    List<String> cells = new ArrayList<>();
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) cells.add(r.getString(i));
                    rows.add(cells.toString());
                }
            }
            Collections.sort(rows); result.put(table, rows);
        }
        return result;
    }

    private interface Work { void run() throws Exception; }
    private static void expectSql(int code, Work work) throws Exception {
        try { work.run(); } catch (SQLException e) { check(e.getErrorCode() == code, "SQL constraint " + code); return; }
        throw new AssertionError("Expected SQL constraint " + code);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
