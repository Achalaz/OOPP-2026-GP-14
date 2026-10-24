
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.MedicalDAO;
import lk.ac.ruhuna.ftms.dao.MedicalAttendanceLinkDAO;
import lk.ac.ruhuna.ftms.model.Medical;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MedicalService {

    private final MedicalDAO medicalDAO;

    public MedicalService() {
        this.medicalDAO = new MedicalDAO();
    }

    public MedicalService(MedicalDAO medicalDAO) {
        this.medicalDAO = Objects.requireNonNull(
                medicalDAO,
                "MedicalDAO cannot be null."
        );
    }

    // Students view only their own medical records
    public List<Medical> getMyMedicals(
            lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity identity
    ) throws SQLException {

        return new StudentAccessService(identity)
                .read(medicalDAO::findByStudentId);
    }

    // Existing staff authorization
    private void requireStaff() {

        var user =
                lk.ac.ruhuna.ftms.util.SessionManager
                        .getCurrentUser();

        if (user == null
                || !user.isActive()
                || user.getRole() == null
                || !Set.of(
                "ADMIN",
                "TECHNICAL_OFFICER"
        ).contains(user.getRole())) {

            throw new SecurityException(
                    "Staff access is required. "
                            + "Students can only view their own medical records.");
        }
    }

    // Lecturer receives restricted medical status data
    public lk.ac.ruhuna.ftms.model.LecturerTable
    getLecturerStatuses(int offeringId) throws SQLException {

        var access = new LecturerAccessService();

        access.requireOffering(offeringId);

        var rows =
                new lk.ac.ruhuna.ftms.dao.LecturerViewDAO()
                        .medicals(offeringId);

        access.requireOffering(offeringId);

        return rows;
    }

    // Submit medical
    public Medical submitMedical(Medical medical)
            throws SQLException {

        requireStaff();
        validateMedical(medical);

        if (medical.getMedicalId() > 0) {

            throw new IllegalArgumentException(
                    "New medical must not have an existing ID.");
        }

        medical.setStatus("PENDING");
        medical.setReviewedBy(null);
        medical.setReviewedAt(null);
        medical.setReviewNote(null);

        medicalDAO.save(medical);

        return medical;
    }

    // Update pending medical
    public boolean updatePendingMedical(Medical medical)
            throws SQLException {

        requireStaff();
        validateMedical(medical);

        if (medical.getMedicalId() <= 0) {

            throw new IllegalArgumentException(
                    "Valid medical ID is required.");
        }

        Medical existing =
                medicalDAO.findById(medical.getMedicalId());

        if (existing == null) {

            throw new IllegalArgumentException(
                    "Medical record not found.");
        }

        if (!"PENDING".equals(existing.getStatus())) {

            throw new IllegalStateException(
                    "Only pending medical records can be updated.");
        }

        if (existing.getStudentId() != medical.getStudentId()) {

            throw new IllegalArgumentException(
                    "Student ID cannot be changed.");
        }

        // Do not invalidate existing attendance links
        boolean changesLinkScope =
                !Objects.equals(
                        existing.getOfferingId(),
                        medical.getOfferingId()
                )
                        || !Objects.equals(
                        existing.getFromDate(),
                        medical.getFromDate()
                )
                        || !Objects.equals(
                        existing.getToDate(),
                        medical.getToDate()
                );

        if (changesLinkScope
                && new MedicalAttendanceLinkDAO()
                .hasLinks(medical.getMedicalId())) {

            throw new IllegalStateException(
                    "Unlink the medical's attendance records "
                            + "before changing its dates or course offering.");
        }

        medical.setStatus("PENDING");

        return medicalDAO.update(medical);
    }

    // Approve medical
    public boolean approveMedical(
            int medicalId,
            int reviewedBy,
            String reviewNote) throws SQLException {

        return reviewMedical(
                medicalId,
                "APPROVED",
                reviewedBy,
                reviewNote
        );
    }

    // Reject medical
    public boolean rejectMedical(
            int medicalId,
            int reviewedBy,
            String reviewNote) throws SQLException {

        return reviewMedical(
                medicalId,
                "REJECTED",
                reviewedBy,
                reviewNote
        );
    }

    // Common approve/reject logic
    private boolean reviewMedical(
            int medicalId,
            String status,
            int reviewedBy,
            String reviewNote) throws SQLException {

        requireStaff();

        validateId(medicalId, "Medical ID");
        validateId(reviewedBy, "Reviewer ID");

        if (reviewNote != null && reviewNote.length() > 500) {

            throw new IllegalArgumentException(
                    "Review note cannot exceed 500 characters.");
        }

        Medical medical =
                medicalDAO.findById(medicalId);

        if (medical == null) {

            throw new IllegalArgumentException(
                    "Medical record not found.");
        }

        if (!"PENDING".equals(medical.getStatus())) {

            throw new IllegalStateException(
                    "This medical record has already been reviewed.");
        }

        boolean updated = medicalDAO.updateStatus(
                medicalId,
                status,
                reviewedBy,
                cleanText(reviewNote)
        );

        if (!updated) {

            throw new IllegalStateException(
                    "Medical review could not be completed. "
                            + "The record may have changed.");
        }

        return true;
    }

    // Find medical by ID
    public Medical getMedicalById(int medicalId)
            throws SQLException {

        requireStaff();
        validateId(medicalId, "Medical ID");

        return medicalDAO.findById(medicalId);
    }

    // Find medical records of student
    public List<Medical> getMedicalsByStudent(
            int studentId) throws SQLException {

        requireStaff();
        validateId(studentId, "Student ID");

        return medicalDAO.findByStudentId(studentId);
    }

    // Load all medicals
    public List<Medical> getAllMedicals()
            throws SQLException {

        requireStaff();

        return medicalDAO.findAll();
    }

    // Delete pending medical
    public boolean deletePendingMedical(
            int medicalId,
            int studentId) throws SQLException {

        requireStaff();

        validateId(medicalId, "Medical ID");
        validateId(studentId, "Student ID");

        Medical medical =
                medicalDAO.findById(medicalId);

        if (medical == null) {

            throw new IllegalArgumentException(
                    "Medical record not found.");
        }

        if (medical.getStudentId() != studentId) {

            throw new IllegalArgumentException(
                    "Medical does not belong to this student.");
        }

        if (!"PENDING".equals(medical.getStatus())) {

            throw new IllegalStateException(
                    "Only pending medical records can be deleted.");
        }

        return medicalDAO.delete(medicalId);
    }

    // Medical validation
    private void validateMedical(Medical medical) {

        if (medical == null) {

            throw new IllegalArgumentException(
                    "Medical record cannot be null.");
        }

        validateId(medical.getStudentId(), "Student ID");

        Integer offeringId = medical.getOfferingId();

        if (offeringId != null && offeringId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid course offering ID.");
        }

        LocalDate fromDate = medical.getFromDate();
        LocalDate toDate = medical.getToDate();

        if (fromDate == null) {

            throw new IllegalArgumentException(
                    "Medical start date is required.");
        }

        if (toDate == null) {

            throw new IllegalArgumentException(
                    "Medical end date is required.");
        }

        if (toDate.isBefore(fromDate)) {

            throw new IllegalArgumentException(
                    "End date cannot be before start date.");
        }

        String reason = cleanText(
                medical.getReason()
        );

        String documentPath = cleanText(
                medical.getDocumentPath()
        );

        if (reason != null && reason.length() > 500) {

            throw new IllegalArgumentException(
                    "Medical reason cannot exceed 500 characters.");
        }

        if (documentPath != null
                && documentPath.length() > 500) {

            throw new IllegalArgumentException(
                    "Document path cannot exceed 500 characters.");
        }

        medical.setReason(reason);
        medical.setDocumentPath(documentPath);
    }

    private void validateId(int id, String fieldName) {

        if (id <= 0) {

            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero.");
        }
    }

    private String cleanText(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
