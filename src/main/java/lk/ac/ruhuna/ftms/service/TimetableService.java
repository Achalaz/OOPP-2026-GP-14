
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
 * Read-only timetable access for Technical Officers.
 * Officers can view only their department timetable.
 */
public final class TimetableService {

    private final TimetableDAO timetableDAO;
    private final AttendanceAccessDAO accessDAO;
    private final SessionIdentity loginIdentity;
    private final ConnectionProvider connections;

    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException; }

    public TimetableService() {
        this(
                new TimetableDAO(),
                new AttendanceAccessDAO()
        );
    }

    public TimetableService(
            TimetableDAO timetableDAO,
            AttendanceAccessDAO accessDAO) {
        this(timetableDAO, accessDAO, DBConnection::getConnection);
    }

    TimetableService(TimetableDAO timetableDAO, AttendanceAccessDAO accessDAO, ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);

        this.timetableDAO = Objects.requireNonNull(
                timetableDAO,
                "TimetableDAO is required."
        );

        this.accessDAO = Objects.requireNonNull(
                accessDAO,
                "AttendanceAccessDAO is required."
        );

        this.loginIdentity =
                SessionManager.requireSession();

        requireTechnicalOfficer();
    }

    // Load department course offerings
    public List<AttendanceOffering> getAuthorizedOfferings() {

        return inDepartment(
                (connection, departmentId) ->
                        List.copyOf(
                                timetableDAO.findOfferings(connection, departmentId, false)
                        )
        );
    }

    // View department timetable
    public List<TimetableEntry> getDepartmentTimetable() {

        return inDepartment(
                (connection, departmentId) ->
                        timetableDAO.findByDepartment(
                                connection,
                                departmentId
                        )
        );
    }

    // View selected course timetable
    public List<TimetableEntry> getOfferingTimetable(
            int offeringId) {

        if (offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        return inDepartment(
                (connection, departmentId) -> {

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

    // Verify original Technical Officer login
    private void requireTechnicalOfficer() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active Technical Officer session is required."
            );
        }
    }

    @FunctionalInterface
    private interface DepartmentQuery<T> {

        T run(
                Connection connection,
                int departmentId
        ) throws SQLException;
    }

    // Run authorized read-only department query
    private <T> T inDepartment(
            DepartmentQuery<T> query) {

        requireTechnicalOfficer();

        try (Connection connection =
                     connections.open()) {

            connection.setAutoCommit(false);

            try {

                int departmentId =
                        accessDAO.requireOfficerDepartment(
                                connection,
                                loginIdentity.userId()
                        );

                T result = query.run(
                        connection,
                        departmentId
                );

                requireTechnicalOfficer();

                connection.commit();

                return result;

            } catch (SQLException | RuntimeException error) {

                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
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
