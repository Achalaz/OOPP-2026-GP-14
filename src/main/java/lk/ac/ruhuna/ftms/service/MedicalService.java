
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.MedicalDAO;
import lk.ac.ruhuna.ftms.model.Medical;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class MedicalService {

    /** Deliberately excludes reason, document path and reviewer notes. */
    public lk.ac.ruhuna.ftms.model.LecturerTable getLecturerStatuses(int offeringId) throws SQLException {
        var access = new LecturerAccessService();
        access.requireOffering(offeringId);
        var rows = new lk.ac.ruhuna.ftms.dao.LecturerViewDAO().medicals(offeringId);
        access.requireOffering(offeringId);
        return rows;
    }

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

    // Submit a new medical record
    public Medical submitMedical(Medical medical)
            throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateMedical(medical);

        if (medical.getMedicalId() > 0) {
            throw new IllegalArgumentException(
                    "New medical must not have an existing ID."
            );
        }

        medical.setStatus("PENDING");
        medical.setReviewedBy(null);
        medical.setReviewedAt(null);
        medical.setReviewNote(null);

        medicalDAO.save(medical);

        return medical;
    }

    // Update a medical record while it is pending
    public boolean updatePendingMedical(Medical medical)
            throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateMedical(medical);

        if (medical.getMedicalId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid medical ID is required."
            );
        }

        Medical existing = medicalDAO.findById(
                medical.getMedicalId()
        );

        if (existing == null) {
            throw new IllegalArgumentException(
                    "Medical record not found."
            );
        }

        if (!"PENDING".equals(existing.getStatus())) {
            throw new IllegalStateException(
                    "Only pending medical records can be updated."
            );
        }

        // The owner of an existing medical must not change
        if (existing.getStudentId() != medical.getStudentId()) {
            throw new IllegalArgumentException(
                    "Student ID cannot be changed."
            );
        }

        medical.setStatus("PENDING");

        return medicalDAO.update(medical);
    }

    // Approve a pending medical record
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

    // Reject a pending medical record
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

    // Common approval and rejection logic
    private boolean reviewMedical(
            int medicalId,
            String status,
            int reviewedBy,
            String reviewNote) throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateId(medicalId, "Medical ID");
        validateId(reviewedBy, "Reviewer ID");

        if (reviewNote != null && reviewNote.length() > 500) {
            throw new IllegalArgumentException(
                    "Review note cannot exceed 500 characters."
            );
        }

        Medical medical = medicalDAO.findById(medicalId);

        if (medical == null) {
            throw new IllegalArgumentException(
                    "Medical record not found."
            );
        }

        if (!"PENDING".equals(medical.getStatus())) {
            throw new IllegalStateException(
                    "This medical record has already been reviewed."
            );
        }

        // DAO also checks PENDING in SQL,
        // preventing a second review from overwriting the first.
        boolean updated = medicalDAO.updateStatus(
                medicalId,
                status,
                reviewedBy,
                cleanText(reviewNote)
        );

        if (!updated) {
            throw new IllegalStateException(
                    "Medical review could not be completed. "
                            + "The record may have changed."
            );
        }

        return true;
    }

    // Get a medical record by ID
    public Medical getMedicalById(int medicalId)
            throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateId(medicalId, "Medical ID");

        return medicalDAO.findById(medicalId);
    }

    // Get all medical records of a student
    public List<Medical> getMedicalsByStudent(
            int studentId) throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateId(studentId, "Student ID");

        return medicalDAO.findByStudentId(studentId);
    }

    // Get all medical records
    // Caller must check staff permissions
    public List<Medical> getAllMedicals()
            throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        return medicalDAO.findAll();
    }

    // Delete a pending medical record belonging
    // to the specified student
    public boolean deletePendingMedical(
            int medicalId,
            int studentId) throws SQLException {
        LecturerAccessService.rejectUnscopedLecturerOperation();

        validateId(medicalId, "Medical ID");
        validateId(studentId, "Student ID");

        Medical medical = medicalDAO.findById(medicalId);

        if (medical == null) {
            throw new IllegalArgumentException(
                    "Medical record not found."
            );
        }

        if (medical.getStudentId() != studentId) {
            throw new IllegalArgumentException(
                    "Medical does not belong to this student."
            );
        }

        if (!"PENDING".equals(medical.getStatus())) {
            throw new IllegalStateException(
                    "Only pending medical records can be deleted."
            );
        }

        return medicalDAO.delete(medicalId);
    }

    // Validate medical details before saving
    private void validateMedical(Medical medical) {

        if (medical == null) {
            throw new IllegalArgumentException(
                    "Medical record cannot be null."
            );
        }

        validateId(medical.getStudentId(), "Student ID");

        Integer offeringId = medical.getOfferingId();

        if (offeringId != null && offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        LocalDate fromDate = medical.getFromDate();
        LocalDate toDate = medical.getToDate();

        if (fromDate == null) {
            throw new IllegalArgumentException(
                    "Medical start date is required."
            );
        }

        if (toDate == null) {
            throw new IllegalArgumentException(
                    "Medical end date is required."
            );
        }

        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date."
            );
        }

        String reason = cleanText(medical.getReason());
        String documentPath = cleanText(
                medical.getDocumentPath()
        );

        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException(
                    "Medical reason cannot exceed 500 characters."
            );
        }

        if (documentPath != null
                && documentPath.length() > 500) {

            throw new IllegalArgumentException(
                    "Document path cannot exceed 500 characters."
            );
        }

        medical.setReason(reason);
        medical.setDocumentPath(documentPath);
    }

    // Validate database IDs
    private void validateId(int id, String fieldName) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero."
            );
        }
    }

    // Remove surrounding spaces and convert
    // empty optional text into null
    private String cleanText(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
