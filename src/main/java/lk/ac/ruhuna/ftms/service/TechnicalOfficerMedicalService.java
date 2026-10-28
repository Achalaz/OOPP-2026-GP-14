
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.OfficerMedicalDAO;

import lk.ac.ruhuna.ftms.database.DBConnection;

import lk.ac.ruhuna.ftms.model.Medical;
import lk.ac.ruhuna.ftms.model.User;

import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Technical Officer Medical Service.
 *
 * Features:
 * - Department Medical View
 * - Student Medical View
 * - Create Medical
 * - Update Pending Medical
 * - Approve / Reject Medical
 * - Delete Pending Medical
 *
 * All operations require an authorized
 * Technical Officer login session.
 */
public final class TechnicalOfficerMedicalService {

    private final OfficerMedicalDAO medicalDAO;
    private final AttendanceAccessDAO accessDAO;

    private final SessionIdentity loginIdentity;

    // ==========================================
    // CONSTRUCTORS
    // ==========================================

    public TechnicalOfficerMedicalService() {

        this(
                new OfficerMedicalDAO(),
                new AttendanceAccessDAO()
        );
    }

    public TechnicalOfficerMedicalService(
            OfficerMedicalDAO medicalDAO,
            AttendanceAccessDAO accessDAO) {

        this.medicalDAO = Objects.requireNonNull(
                medicalDAO
        );

        this.accessDAO = Objects.requireNonNull(
                accessDAO
        );

        this.loginIdentity =
                SessionManager.requireSession();

        requireOfficerSession();
    }

    // ==========================================
    // VIEW DEPARTMENT MEDICALS
    // ==========================================

    public List<Medical> getDepartmentMedicals()
            throws SQLException {

        return inDepartment(
                (connection, departmentId) ->
                        medicalDAO.findByDepartment(
                                connection,
                                departmentId
                        )
        );
    }

    // ==========================================
    // VIEW STUDENT MEDICALS
    // ==========================================

    public List<Medical> getStudentMedicals(
            int studentId) throws SQLException {

        positive(studentId, "Student ID");

        return inDepartment(
                (connection, departmentId) -> {

                    requireDepartmentStudent(
                            connection,
                            departmentId,
                            studentId
                    );

                    return medicalDAO.findByStudent(
                            connection,
                            departmentId,
                            studentId
                    );
                }
        );
    }

    // ==========================================
    // VIEW SINGLE MEDICAL
    // ==========================================

    public Medical getMedical(
            int medicalId) throws SQLException {

        positive(medicalId, "Medical ID");

        return inDepartment(
                (connection, departmentId) ->
                        medicalDAO.findOwned(
                                connection,
                                departmentId,
                                medicalId,
                                false
                        )
        );
    }

    // ==========================================
    // CREATE MEDICAL
    // ==========================================

    public Medical createMedical(
            Medical medical) throws SQLException {

        validateMedical(medical);

        if (medical.getMedicalId() != 0) {

            throw new IllegalArgumentException(
                    "A new medical cannot already have an ID."
            );
        }

        return inDepartment(
                (connection, departmentId) -> {

                    requireDepartmentStudent(
                            connection,
                            departmentId,
                            medical.getStudentId()
                    );

                    requireValidOffering(
                            connection,
                            departmentId,
                            medical.getStudentId(),
                            medical.getOfferingId()
                    );

                    int newId =
                            medicalDAO.insert(
                                    connection,
                                    medical
                            );

                    return medicalDAO.findOwned(
                            connection,
                            departmentId,
                            newId,
                            false
                    );
                }
        );
    }

    // ==========================================
    // UPDATE PENDING MEDICAL
    // ==========================================

    public Medical updatePendingMedical(
            Medical medical) throws SQLException {

        validateMedical(medical);

        positive(
                medical.getMedicalId(),
                "Medical ID"
        );

        return inDepartment(
                (connection, departmentId) -> {

                    Medical existing =
                            requireOwnedMedical(
                                    connection,
                                    departmentId,
                                    medical.getMedicalId()
                            );

                    requirePending(existing);

                    if (existing.getStudentId()
                            != medical.getStudentId()) {

                        throw new IllegalArgumentException(
                                "A medical's student cannot be changed."
                        );
                    }

                    requireValidOffering(
                            connection,
                            departmentId,
                            medical.getStudentId(),
                            medical.getOfferingId()
                    );

                    if (!medicalDAO.updatePending(
                            connection,
                            medical)) {

                        throw new IllegalStateException(
                                "Pending medical could not be updated."
                        );
                    }

                    return medicalDAO.findOwned(
                            connection,
                            departmentId,
                            medical.getMedicalId(),
                            false
                    );
                }
        );
    }

    // ==========================================
    // APPROVE MEDICAL
    // ==========================================

    public Medical approveMedical(
            int medicalId,
            String reviewNote) throws SQLException {

        return reviewMedical(
                medicalId,
                "APPROVED",
                reviewNote
        );
    }

    // ==========================================
    // REJECT MEDICAL
    // ==========================================

    public Medical rejectMedical(
            int medicalId,
            String reviewNote) throws SQLException {

        return reviewMedical(
                medicalId,
                "REJECTED",
                reviewNote
        );
    }

    // ==========================================
    // REVIEW MEDICAL
    // ==========================================

    private Medical reviewMedical(
            int medicalId,
            String status,
            String reviewNote) throws SQLException {

        positive(
                medicalId,
                "Medical ID"
        );

        String note = cleaned(
                reviewNote,
                500,
                "Review note"
        );

        return inDepartment(
                (connection, departmentId) -> {

                    Medical existing =
                            requireOwnedMedical(
                                    connection,
                                    departmentId,
                                    medicalId
                            );

                    requirePending(existing);

                    // Always use the logged-in TO ID
                    if (!medicalDAO.reviewPending(
                            connection,
                            medicalId,
                            status,
                            loginIdentity.userId(),
                            note)) {

                        throw new IllegalStateException(
                                "This medical may already be reviewed."
                        );
                    }

                    return medicalDAO.findOwned(
                            connection,
                            departmentId,
                            medicalId,
                            false
                    );
                }
        );
    }

    // ==========================================
    // DELETE PENDING MEDICAL
    // ==========================================

    public void deletePendingMedical(
            int medicalId) throws SQLException {

        positive(
                medicalId,
                "Medical ID"
        );

        inDepartment(
                (connection, departmentId) -> {

                    Medical existing =
                            requireOwnedMedical(
                                    connection,
                                    departmentId,
                                    medicalId
                            );

                    requirePending(existing);

                    if (!medicalDAO.deletePending(
                            connection,
                            medicalId)) {

                        throw new IllegalStateException(
                                "Pending medical could not be deleted."
                        );
                    }

                    return null;
                }
        );
    }

    // ==========================================
    // DEPARTMENT OWNERSHIP
    // ==========================================

    private Medical requireOwnedMedical(
            Connection connection,
            int departmentId,
            int medicalId) throws SQLException {

        Medical result =
                medicalDAO.findOwned(
                        connection,
                        departmentId,
                        medicalId,
                        true
                );

        if (result == null) {

            throw new SecurityException(
                    "Medical not found in your department."
            );
        }

        return result;
    }

    // ==========================================
    // VERIFY STUDENT DEPARTMENT
    // ==========================================

    private void requireDepartmentStudent(
            Connection connection,
            int departmentId,
            int studentId) throws SQLException {

        String sql = """
                SELECT 1
                FROM students s
                JOIN users u
                    ON u.user_id = s.student_id
                WHERE s.student_id = ?
                  AND u.role = 'STUDENT'
                  AND u.is_active = 1
                  AND u.department_id = ?
                FOR SHARE
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, studentId);
            ps.setInt(2, departmentId);

            try (ResultSet rows =
                         ps.executeQuery()) {

                if (!rows.next()) {

                    throw new SecurityException(
                            "Student is not active in your department."
                    );
                }
            }
        }
    }

    // ==========================================
    // VERIFY COURSE OFFERING
    // ==========================================

    private void requireValidOffering(
            Connection connection,
            int departmentId,
            int studentId,
            Integer offeringId) throws SQLException {

        // Medical can be general
        if (offeringId == null) {
            return;
        }

        positive(
                offeringId,
                "Offering ID"
        );

        // Check offering belongs to TO department
        accessDAO.requireOffering(
                connection,
                departmentId,
                offeringId
        );

        // Check student enrollment
        String sql = """
                SELECT 1
                FROM enrollments e
                WHERE e.student_id = ?
                  AND e.offering_id = ?
                FOR SHARE
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {

            ps.setInt(1, studentId);
            ps.setInt(2, offeringId);

            try (ResultSet rows =
                         ps.executeQuery()) {

                if (!rows.next()) {

                    throw new IllegalArgumentException(
                            "The student is not enrolled "
                                    + "in that course offering."
                    );
                }
            }
        }
    }

    // ==========================================
    // CHECK PENDING STATUS
    // ==========================================

    private static void requirePending(
            Medical medical) {

        if (!"PENDING".equals(
                medical.getStatus())) {

            throw new IllegalStateException(
                    "Only PENDING medical records can be changed."
            );
        }
    }

    // ==========================================
    // VERIFY TECHNICAL OFFICER SESSION
    // ==========================================

    private void requireOfficerSession() {

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
                    "An active Technical Officer session is required."
            );
        }
    }

    // ==========================================
    // VALIDATE MEDICAL INPUT
    // ==========================================

    private static void validateMedical(
            Medical medical) {

        Objects.requireNonNull(
                medical,
                "Medical details are required."
        );

        positive(
                medical.getStudentId(),
                "Student ID"
        );

        if (medical.getOfferingId() != null) {

            positive(
                    medical.getOfferingId(),
                    "Offering ID"
            );
        }

        LocalDate from =
                medical.getFromDate();

        LocalDate to =
                medical.getToDate();

        if (from == null
                || to == null
                || to.isBefore(from)) {

            throw new IllegalArgumentException(
                    "Provide a valid medical date range."
            );
        }

        medical.setReason(
                cleaned(
                        medical.getReason(),
                        500,
                        "Reason"
                )
        );

        medical.setDocumentPath(
                cleaned(
                        medical.getDocumentPath(),
                        500,
                        "Document path"
                )
        );
    }

    // ==========================================
    // CLEAN STRING INPUT
    // ==========================================

    private static String cleaned(
            String value,
            int max,
            String label) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        String result = value.strip();

        if (result.length() > max) {

            throw new IllegalArgumentException(
                    label
                            + " exceeds "
                            + max
                            + " characters."
            );
        }

        return result;
    }

    // ==========================================
    // VALIDATE POSITIVE ID
    // ==========================================

    private static void positive(
            int value,
            String name) {

        if (value <= 0) {

            throw new IllegalArgumentException(
                    name + " must be positive."
            );
        }
    }

    // ==========================================
    // DATABASE TRANSACTION INTERFACE
    // ==========================================

    @FunctionalInterface
    private interface DepartmentAction<T> {

        T run(
                Connection connection,
                int departmentId
        ) throws SQLException;
    }

    // ==========================================
    // AUTHORIZED TRANSACTION
    // ==========================================

    private <T> T inDepartment(
            DepartmentAction<T> action) throws SQLException {

        // Verify login
        requireOfficerSession();

        try (Connection connection =
                     DBConnection.getConnection()) {

            // Begin transaction
            connection.setAutoCommit(false);

            try {

                // Determine authorized department
                int departmentId =
                        accessDAO.requireOfficerDepartment(
                                connection,
                                loginIdentity.userId()
                        );

                // Execute operation
                T result =
                        action.run(
                                connection,
                                departmentId
                        );

                // Recheck original session
                requireOfficerSession();

                // Commit transaction
                connection.commit();

                return result;

            } catch (
                    SQLException | RuntimeException error
            ) {

                // Rollback on error
                try {

                    connection.rollback();

                } catch (SQLException rollbackError) {

                    error.addSuppressed(
                            rollbackError
                    );
                }

                throw error;
            }
        }
    }
}
