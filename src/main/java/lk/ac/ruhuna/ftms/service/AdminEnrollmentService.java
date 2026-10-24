package lk.ac.ruhuna.ftms.service;

import java.sql.*;
import java.util.List;
import java.util.Objects;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.Enrollment;
import lk.ac.ruhuna.ftms.util.SessionManager;

/** Admin enrollment writes are transactional; only the status of existing enrollments can change. */
public final class AdminEnrollmentService {
    // Verified against ftms_group14.enrollments on 2026-10-23. No upper attempt limit exists in the schema.
    public static final List<String> STATUSES = List.of("ENROLLED", "WITHDRAWN", "COMPLETED", "REPEAT");
    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();
    private final EnrollmentDAO dao = new EnrollmentDAO();

    public AdminEnrollmentService() { requireSession(); }

    public void requireSession() {
        SessionManager.requireSession(identity);
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"ADMIN".equals(user.getRole()))
            throw new SecurityException("Active administrator access is required to manage enrollments.");
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Operation cancelled.");
    }

    public List<Enrollment> findAllEnrollments() throws SQLException { return execute(dao::findAllEnrollments); }
    public List<Enrollment> searchEnrollments(String query) throws SQLException { return execute(c -> dao.searchEnrollments(c, query)); }
    public List<EnrollmentDAO.EligibleStudent> findEligibleStudents(String query) throws SQLException {
        return execute(c -> dao.findEligibleStudents(c, query));
    }
    public List<CourseOffering> findAvailableOfferings(String query) throws SQLException {
        return execute(c -> dao.findAvailableOfferings(c, query));
    }
    public Enrollment findEnrollmentById(int id) throws SQLException { return execute(c -> dao.findEnrollmentById(c, id, false)); }

    public int enrollStudent(int student, int offering, int attempt) throws SQLException {
        requireSession();
        if (attempt < 1) throw new IllegalArgumentException("Attempt number must be a positive whole number.");
        try {
            return execute(c -> {
                dao.requireEligibleStudent(c, student);
                requireOpenOffering(dao.lockOfferingStatus(c, offering));
                return dao.enrollStudent(c, student, offering, attempt, attempt == 1 ? "ENROLLED" : "REPEAT");
            });
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062)
                throw new IllegalArgumentException("This student already has an enrollment for this offering and attempt. Select the existing record to update its status.");
            throw error;
        }
    }

    public void updateEnrollmentStatus(int id, String expectedStatus, String status) throws SQLException {
        requireSession();
        if (status == null || !STATUSES.contains(status)) throw new IllegalArgumentException("Select a valid enrollment status.");
        execute(c -> {
            Enrollment enrollment = dao.findEnrollmentById(c, id, true);
            if (enrollment == null) throw new IllegalArgumentException("Enrollment no longer exists. Refresh and try again.");
            if (!Objects.equals(enrollment.status(), expectedStatus))
                throw new IllegalArgumentException("This enrollment changed since it was loaded. Refresh before updating it.");
            dao.requireEligibleStudent(c, enrollment.studentId());
            String offeringStatus = dao.lockOfferingStatus(c, enrollment.offeringId());
            if ("ENROLLED".equals(status) || "REPEAT".equals(status)) requireOpenOffering(offeringStatus);
            if ("CANCELLED".equals(offeringStatus) && !"WITHDRAWN".equals(status))
                throw new IllegalArgumentException("Cancelled offerings only permit withdrawal.");
            if (!enrollment.status().equals(status)) dao.updateEnrollmentStatus(c, id, status);
            return null;
        });
    }

    private static void requireOpenOffering(String status) {
        if (!List.of("PLANNED", "ACTIVE").contains(status))
            throw new IllegalArgumentException("Only planned or active offerings accept enrollments.");
    }

    @FunctionalInterface private interface Operation<T> { T run(Connection c) throws SQLException; }
    private <T> T execute(Operation<T> operation) throws SQLException {
        requireSession();
        try (var connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("""
                        SELECT user_id FROM users WHERE user_id=? AND role='ADMIN' AND is_active=TRUE FOR SHARE
                        """)) {
                    statement.setInt(1, identity.userId());
                    statement.setQueryTimeout(15);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SecurityException("Your administrator account is no longer active. Sign in again.");
                    }
                }
                T result = operation.run(connection);
                requireSession();
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }
}
