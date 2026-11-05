package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.MedicalAttendanceLinkDAO;
import lk.ac.ruhuna.ftms.dao.MedicalAttendanceLinkDAO.AbsenceRow;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MedicalAttendanceLinkService {

    private final SessionManager.SessionIdentity identity =
            SessionManager.requireSession();

    private final AttendanceAccessDAO access =
            new AttendanceAccessDAO();

    private final MedicalAttendanceLinkDAO links =
            new MedicalAttendanceLinkDAO();

    @FunctionalInterface
    private interface Work<T> {
        T run(
                Connection connection,
                int departmentId
        ) throws SQLException;
    }

    // Only active Technical Officers may manage links
    private void requireOfficer() {

        SessionManager.requireSession(identity);

        var user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole())) {

            throw new SecurityException(
                    "Only an active Technical Officer "
                            + "can link medical absences.");
        }
    }

    // Safe database transaction
    private <T> T transaction(Work<T> work)
            throws SQLException {

        requireOfficer();

        try (Connection connection =
                     DBConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {

                int departmentId =
                        access.requireOfficerDepartment(
                                connection,
                                identity.userId()
                        );

                T result = work.run(
                        connection,
                        departmentId
                );

                requireOfficer();

                connection.commit();

                return result;

            } catch (SQLException | RuntimeException error) {

                try {
                    connection.rollback();

                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }

                throw error;
            }
        }
    }

    private static void validIds(
            int medicalId,
            int attendanceRecordId) {

        if (medicalId <= 0 || attendanceRecordId <= 0) {

            throw new IllegalArgumentException(
                    "Select a valid medical and attendance record.");
        }
    }

    // Load absences related to a medical
    public List<AbsenceRow> getCandidateAbsences(
            int medicalId) throws SQLException {

        if (medicalId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid medical ID.");
        }

        return transaction((connection, departmentId) -> {

            var medical =
                    links.requireMedical(connection, medicalId);

            if (medical.offeringId() != null) {

                access.requireOffering(
                        connection,
                        departmentId,
                        medical.offeringId()
                );
            }

            return links.findCandidates(
                    connection,
                    medicalId,
                    medical,
                    departmentId
            );
        });
    }

    // Link medical and attendance absence
    public void linkAbsence(
            int medicalId,
            int attendanceRecordId) throws SQLException {

        validIds(medicalId, attendanceRecordId);

        transaction((connection, departmentId) -> {

            var medical =
                    links.requireMedical(connection, medicalId);

            if (medical.offeringId() != null) {

                access.requireOffering(
                        connection,
                        departmentId,
                        medical.offeringId()
                );
            }

            links.lockAttendanceRecord(
                    connection,
                    attendanceRecordId
            );

            AbsenceRow target =
                    links.findCandidates(
                                    connection,
                                    medicalId,
                                    medical,
                                    departmentId
                            )
                            .stream()
                            .filter(row ->
                                    row.attendanceRecordId()
                                            == attendanceRecordId)
                            .findFirst()
                            .orElseThrow(() ->
                                    new SecurityException(
                                            "Selected attendance is not "
                                                    + "a matching absence for this medical."));

            if (!"ABSENT".equals(target.attendanceStatus())
                    && !"MEDICAL".equals(
                    target.attendanceStatus())) {

                throw new IllegalArgumentException(
                        "Only ABSENT or MEDICAL records may be linked.");
            }

            if (target.linkedMedicalId() != null) {

                throw new IllegalStateException(
                        "This absence is already linked to a medical.");
            }

            links.insertLink(
                    connection,
                    medicalId,
                    attendanceRecordId,
                    identity.userId()
            );

            return null;
        });
    }

    // Remove existing medical-attendance link
    public void unlinkAbsence(
            int medicalId,
            int attendanceRecordId) throws SQLException {

        validIds(medicalId, attendanceRecordId);

        transaction((connection, departmentId) -> {

            var medical =
                    links.requireMedical(connection, medicalId);

            if (medical.offeringId() != null) {

                access.requireOffering(
                        connection,
                        departmentId,
                        medical.offeringId()
                );
            }

            links.lockAttendanceRecord(
                    connection,
                    attendanceRecordId
            );

            AbsenceRow target =
                    links.findCandidates(
                                    connection,
                                    medicalId,
                                    medical,
                                    departmentId
                            )
                            .stream()
                            .filter(row ->
                                    row.attendanceRecordId()
                                            == attendanceRecordId)
                            .findFirst()
                            .orElseThrow(() ->
                                    new SecurityException(
                                            "Selected attendance does not belong "
                                                    + "to this medical period and department."));

            if (target.linkedMedicalId() == null
                    || target.linkedMedicalId() != medicalId) {

                throw new IllegalArgumentException(
                        "This attendance is not linked "
                                + "to the selected medical.");
            }

            if (!links.removeLink(
                    connection,
                    medicalId,
                    attendanceRecordId)) {

                throw new IllegalStateException(
                        "Medical link was changed by another user. "
                                + "Reload the list.");
            }

            return null;
        });
    }
}
