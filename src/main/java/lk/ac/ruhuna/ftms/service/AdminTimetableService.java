
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.AssignedLecturer;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableInput;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.List;
import java.util.Objects;

/**
 * Admin-only timetable viewing and management.
 */
public final class AdminTimetableService {

    private static final String WRITE_LOCK =
            "ftms_group14_timetable_write";

    private final TimetableDAO timetableDAO;
    private final ConnectionProvider connections;

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }
    private final SessionIdentity loginIdentity;

    // Default constructor
    public AdminTimetableService() {
        this(new TimetableDAO(), DBConnection::getConnection);
    }

    // Dependency injection constructor
    public AdminTimetableService(
            TimetableDAO timetableDAO) {
        this(timetableDAO, DBConnection::getConnection);
    }

    AdminTimetableService(TimetableDAO timetableDAO, ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);

        this.timetableDAO = Objects.requireNonNull(
                timetableDAO,
                "TimetableDAO is required."
        );

        this.loginIdentity =
                SessionManager.requireSession();

        requireAdminSession();
    }

    // ======================================
    // VIEW ALL COURSE OFFERINGS
    // ======================================

    public List<AttendanceOffering> getAllOfferings() {
        return execute(false, connection -> timetableDAO.findOfferings(connection, null, false));
    }

    public List<AttendanceOffering> getSchedulableOfferings() {
        return execute(false, connection -> timetableDAO.findOfferings(connection, null, true));
    }

    public List<AssignedLecturer> getAssignedLecturers() {
        return execute(false, timetableDAO::findAssignedLecturers);
    }

    // ======================================
    // VIEW ALL DEPARTMENT TIMETABLES
    // ======================================

    public List<TimetableEntry> getAllTimetable() {
        return execute(false, timetableDAO::findAll);
    }

    // ======================================
    // VIEW ONE OFFERING TIMETABLE
    // ======================================

    public List<TimetableEntry> getOfferingTimetable(
            int offeringId) {

        positive(offeringId, "Offering ID");

        return execute(false, connection -> {

            int departmentId =
                    departmentForOffering(
                            connection,
                            offeringId
                    );

            return timetableDAO.findByOffering(
                    connection,
                    departmentId,
                    offeringId
            );
        });
    }

    // ======================================
    // CREATE TIMETABLE ENTRY
    // ======================================

    public int createTimetableEntry(
            TimetableInput input) {

        Objects.requireNonNull(
                input,
                "Timetable details are required."
        );

        return execute(true, connection -> {

            int departmentId =
                    departmentForOffering(
                            connection,
                            input.offeringId()
                    );

            return timetableDAO.createEntry(
                    connection,
                    departmentId,
                    input
            );
        });
    }

    // ======================================
    // UPDATE TIMETABLE ENTRY
    // ======================================

    public void updateTimetableEntry(
            int timetableId,
            TimetableInput input) {

        positive(timetableId, "Timetable ID");

        Objects.requireNonNull(
                input,
                "Timetable details are required."
        );

        execute(true, connection -> {

            int oldDepartment =
                    departmentForEntry(
                            connection,
                            timetableId
                    );

            int newDepartment =
                    departmentForOffering(
                            connection,
                            input.offeringId()
                    );

            if (oldDepartment != newDepartment) {

                throw new IllegalArgumentException(
                        "Choose an offering from the same "
                                + "department when updating."
                );
            }

            timetableDAO.updateEntry(
                    connection,
                    oldDepartment,
                    timetableId,
                    input
            );

            return null;
        });
    }

    // ======================================
    // DELETE TIMETABLE ENTRY
    // ======================================

    public void deleteTimetableEntry(
            int timetableId) {

        positive(timetableId, "Timetable ID");

        execute(true, connection -> {

            int departmentId =
                    departmentForEntry(
                            connection,
                            timetableId
                    );

            timetableDAO.deleteEntry(
                    connection,
                    departmentId,
                    timetableId
            );

            return null;
        });
    }

    // ======================================
    // ADMIN LOGIN VALIDATION
    // ======================================

    private void requireAdminSession() {

        SessionManager.requireSession(
                loginIdentity
        );

        User user =
                SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole())
                || user.getUserId()
                != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active Admin session is required."
            );
        }
    }

    // Verify Admin account in database
    private void requireAdminInDatabase(
            Connection connection) throws SQLException {

        String sql = """
                SELECT 1
                FROM users
                WHERE user_id = ?
                  AND role = 'ADMIN'
                  AND is_active = 1
                FOR SHARE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    loginIdentity.userId()
            );

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new SecurityException(
                            "Admin account is not active."
                    );
                }
            }
        }
    }

    // ======================================
    // FIND DEPARTMENT FOR OFFERING
    // ======================================

    private static int departmentForOffering(
            Connection connection,
            int offeringId) throws SQLException {

        positive(offeringId, "Offering ID");

        String sql = """
                SELECT c.department_id
                FROM course_offerings o
                JOIN courses c
                    ON c.course_id = o.course_id
                WHERE o.offering_id = ?
                  AND c.department_id IS NOT NULL
                FOR SHARE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new IllegalArgumentException(
                            "Course offering has no valid department."
                    );
                }

                return rs.getInt(1);
            }
        }
    }

    // ======================================
    // FIND DEPARTMENT FOR EXISTING ENTRY
    // ======================================

    private static int departmentForEntry(
            Connection connection,
            int timetableId) throws SQLException {

        String sql = """
                SELECT c.department_id
                FROM timetable_entries t
                JOIN course_offerings o
                    ON o.offering_id = t.offering_id
                JOIN courses c
                    ON c.course_id = o.course_id
                WHERE t.timetable_id = ?
                  AND c.department_id IS NOT NULL
                FOR SHARE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, timetableId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new IllegalArgumentException(
                            "Timetable entry not found."
                    );
                }

                return rs.getInt(1);
            }
        }
    }

    // ======================================
    // DATABASE TRANSACTION
    // ======================================

    @FunctionalInterface
    private interface SqlWork<T> {

        T run(
                Connection connection
        ) throws SQLException;
    }

    private <T> T execute(
            boolean writing,
            SqlWork<T> work) {

        requireAdminSession();
        rejectInterrupted();

        try (Connection connection =
                     connections.open()) {

            connection.setAutoCommit(false);

            boolean lockHeld = false;

            try {

                if (writing) {

                    acquireWriteLock(connection);
                    lockHeld = true;
                }

                requireAdminInDatabase(connection);

                T result = work.run(connection);

                requireAdminSession();
                requireAdminInDatabase(connection);

                rejectInterrupted();
                connection.commit();

                return result;

            } catch (SQLException | RuntimeException error) {

                try {

                    connection.rollback();

                } catch (SQLException rollbackError) {

                    error.addSuppressed(rollbackError);
                }

                throw error;

            } finally {

                if (lockHeld) {

                    try {

                        releaseWriteLock(connection);

                    } catch (SQLException releaseError) {

                        System.err.println(
                                "Timetable lock release failed: "
                                        + releaseError.getMessage()
                        );
                    }
                }
            }

        } catch (SQLException error) {

            throw new IllegalStateException(
                    "Timetable database operation failed.",
                    error
            );
        }
    }

    // ======================================
    // MYSQL WRITE LOCK
    // ======================================

    private static void acquireWriteLock(
            Connection connection) throws SQLException {

        String sql = "SELECT GET_LOCK(?, 10)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    WRITE_LOCK
            );

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()
                        || rs.getInt(1) != 1) {

                    throw new IllegalStateException(
                            "Another timetable change is running. "
                                    + "Try again."
                    );
                }
            }
        }
    }

    // Release database lock
    private static void releaseWriteLock(
            Connection connection) throws SQLException {

        String sql = "SELECT RELEASE_LOCK(?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    WRITE_LOCK
            );

            try (ResultSet rs =
                         statement.executeQuery()) {
                // Result consumed when closing.
            }
        }
    }

    private static void rejectInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Timetable operation cancelled.");
        }
    }

    // Validate IDs
    private static void positive(
            int id,
            String label) {

        if (id <= 0) {

            throw new IllegalArgumentException(
                    label + " must be positive."
            );
        }
    }
}
