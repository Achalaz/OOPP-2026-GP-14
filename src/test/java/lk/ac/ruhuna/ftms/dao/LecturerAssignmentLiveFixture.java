package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.service.LecturerAssignmentService;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.sql.*;
import java.util.*;

/** Owns one unique live offering. Cleanup checks all original database row data. */
public final class LecturerAssignmentLiveFixture implements AutoCloseable {
    public final User admin;
    public final User lecturer;
    public final CourseOffering offering = new CourseOffering();
    private final Map<String,List<String>> original;

    public LecturerAssignmentLiveFixture() throws Exception {
        original = snapshot();
        var users = new UserDAO().getAllUsers();
        admin = users.stream().filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow();
        SessionManager.startSession(admin);
        int lecturerId = new LecturerAssignmentService(new LecturerCourseAssignmentDAO()).getEligibleLecturers().getFirst().lecturerId();
        lecturer = users.stream().filter(u -> u.getUserId()==lecturerId).findFirst().orElseThrow();
        var dao = new CourseOfferingDAO();
        offering.setCourseId(dao.getActiveCourses().getFirst().courseId());
        offering.setSemesterId(dao.getSemesters().getFirst().semesterId());
        offering.setOfferingGroup("LAT_" + UUID.randomUUID().toString().replace("-", "").substring(0,20));
        offering.setStatus("ACTIVE");
        new CourseOfferingService(dao, CourseService::isCurrentUserAdmin).saveOffering(offering);
    }

    @Override public void close() throws Exception {
        try {
            SessionManager.startSession(admin);
            var service = new LecturerAssignmentService(new LecturerCourseAssignmentDAO());
            for (var assignment : service.getAssignmentsByOffering(offering.getOfferingId())) {
                service.removeAssignment(offering.getOfferingId(), assignment.getAssignmentId());
            }
            if (!new CourseOfferingService(new CourseOfferingDAO(), CourseService::isCurrentUserAdmin).deleteOffering(offering.getOfferingId()))
                throw new AssertionError("Temporary offering was not removed.");
            if (!original.equals(snapshot())) throw new AssertionError("Original database rows changed during assignment workflow.");
            System.out.println("Live assignment cleanup passed: temporary offering/assignments removed; all original database rows unchanged.");
        } finally { SessionManager.logout(); }
    }

    private static Map<String,List<String>> snapshot() throws SQLException {
        try (var c = DBConnection.getConnection()) {
            List<String> tables = new ArrayList<>();
            try (var s=c.createStatement(); var r=s.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")) {
                while (r.next()) tables.add(r.getString(1));
            }
            Map<String,List<String>> result = new TreeMap<>();
            for (String table : tables) {
                List<String> rows = new ArrayList<>();
                try (var s=c.createStatement(); var r=s.executeQuery("SELECT * FROM `" + table.replace("`","``") + "`")) {
                    while (r.next()) {
                        List<String> cells = new ArrayList<>();
                        for (int i=1;i<=r.getMetaData().getColumnCount();i++) cells.add(r.getString(i));
                        rows.add(cells.toString());
                    }
                }
                Collections.sort(rows); result.put(table,rows);
            }
            return result;
        }
    }
}
