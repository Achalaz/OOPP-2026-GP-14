
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Read-only department timetable access
 * for Technical Officers.
 *
 * Uses the same department authorization
 * checks as AttendanceService.
 */
public final class TimetableService {

    private final TimetableDAO timetableDAO;
    private final AttendanceAccessDAO accessDAO;
    private final SessionIdentity loginIdentity;

    // Default constructor
    public TimetableService() {

        this(
                new TimetableDAO(),
                new AttendanceAccessDAO()
        );
    }

    // Dependency injection constructor
    public TimetableService(
            TimetableDAO timetableDAO,
            AttendanceAccessDAO accessDAO) {

        this.timetableDAO = Objects.requireNonNull(
                timetableDAO,
                "TimetableDAO is required."
        );

        this.accessDAO = Objects.requireNonNull(
                accessDAO,
                "AttendanceAccessDAO is required."
        );

        // Capture original login session
        this.loginIdentity =
                SessionManager.requireSession();

        // Verify Technical Officer
        requireTechnicalOfficer();
    }

    /**
     * Get course offerings belonging to
     * the logged-in officer's department.
     */
    public List<AttendanceOffering>
    getAuthorizedOfferings() {

        return inDepartment(
                (connection, departmentId) ->
                        List.copyOf(
                                accessDAO.findOfferings(
                                        connection,
                                        departmentId
                                )
                        )
        );
    }

    /**
     * Get all timetable entries belonging
     * to the authorized department.
     */
    public List<TimetableEntry>
    getDepartmentTimetable() {

        return inDepartment(
                (connection, departmentId) ->
                        timetableDAO.findByDepartment(
                                connection,
                                departmentId
                        )
        );
    }

    /**
     * Get timetable entries for
     * one authorized course offering.
     */
    public List<TimetableEntry>
    getOfferingTimetable(int offeringId) {

        if (offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        return inDepartment(
                (connection, departmentId) -> {

                    // Check department ownership
                    accessDAO.requireOffering(
                            connection,
                            departmentId,
                            offeringId
                    );

                    return timetableDAO.findByOffering(
                            connection,
                            departmentId,
                            offeringId
                    );
                }
        );
    }

    /**
     * Verify logged-in user is an
     * active Technical Officer.
     */
    private void requireTechnicalOfficer() {

        // Prevent use after logout or account switch
        SessionManager.requireSession(
                loginIdentity
        );

        User user =
                SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(
                user.getRole()
        )
                || user.getUserId()
                != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active Technical Officer "
                            + "session is required."
            );
        }
    }

    /**
     * Database operation scoped
     * to an authorized department.
     */
    @FunctionalInterface
    private interface DepartmentQuery<T> {

        T run(
                Connection connection,
                int departmentId
        ) throws SQLException;
    }

    /**
     * Execute database queries using
     * the officer's verified department.
     */
    private <T> T inDepartment(
            DepartmentQuery<T> query) {

        // Check session before database access
        requireTechnicalOfficer();

        try (
                Connection connection =
                        DBConnection.getConnection()
        ) {

            // Keep department authorization
            // valid during the query
            connection.setAutoCommit(false);

            try {

                // Department ID comes from database,
                // never from the GUI
                int departmentId =
                        accessDAO.requireOfficerDepartment(
                                connection,
                                loginIdentity.userId()
                        );

                // Run authorized query
                T result = query.run(
                        connection,
                        departmentId
                );

                // Prevent returning data after
                // logout or account switch
                requireTechnicalOfficer();

                connection.commit();

                return result;

            } catch (
                    SQLException | RuntimeException error
            ) {

                try {
                    connection.rollback();

                } catch (SQLException rollbackError) {

                    error.addSuppressed(
                            rollbackError
                    );
                }

                throw error;
            }

        } catch (SQLException error) {

            throw new IllegalStateException(
                    "Could not load the timetable. "
                            + "Check the database connection.",
                    error
            );
        }
    }
}
