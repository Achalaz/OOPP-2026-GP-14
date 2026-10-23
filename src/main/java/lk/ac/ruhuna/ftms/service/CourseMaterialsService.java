package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseMaterialsDAO;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.model.CourseMaterials;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class CourseMaterialsService {

    private final CourseMaterialsDAO materialsDAO;
    private final LecturerCourseAssignmentDAO assignmentDAO;

    private static final Set<String> VALID_TYPES = Set.of(
            "LECTURE_NOTE",
            "PRACTICAL",
            "ASSIGNMENT",
            "LINK",
            "OTHER"
    );

    public CourseMaterialsService(
            CourseMaterialsDAO materialsDAO,
            LecturerCourseAssignmentDAO assignmentDAO) {

        this.materialsDAO =
                Objects.requireNonNull(materialsDAO);

        this.assignmentDAO =
                Objects.requireNonNull(assignmentDAO);
    }

    private int requireLecturer() {

        SessionManager.SessionIdentity identity =
                SessionManager.requireSession();

        User user = SessionManager.getCurrentUser();

        SessionManager.requireSession(identity);

        if (user == null
                || user.getUserId() != identity.userId()
                || !user.isActive()
                || !"LECTURER".equalsIgnoreCase(user.getRole())) {

            throw new SecurityException(
                    "Access denied. Active lecturer login required.");
        }

        return identity.userId();
    }

    private void requireOfferingAccess(
            int lecturerId,
            int offeringId) throws SQLException {

        if (offeringId <= 0
                || !assignmentDAO.isAssignedToOffering(
                lecturerId, offeringId)) {

            throw new SecurityException(
                    "You are not assigned to this course offering.");
        }
    }

    public List<CourseMaterials> getMyMaterials()
            throws SQLException {

        int lecturerId = requireLecturer();

        return materialsDAO.getByUploader(lecturerId);
    }

    public List<CourseMaterials> searchMyMaterials(
            String keyword) throws SQLException {

        int lecturerId = requireLecturer();

        return materialsDAO.searchByUploader(
                lecturerId, keyword);
    }

    public boolean save(CourseMaterials material)
            throws SQLException {

        int lecturerId = requireLecturer();

        if (material == null) {
            throw new IllegalArgumentException(
                    "Material cannot be null.");
        }

        String title = material.getTitle();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter a material title.");
        }

        title = title.trim();

        if (title.length() > 180) {
            throw new IllegalArgumentException(
                    "Title cannot exceed 180 characters.");
        }

        String type = material.getMaterialType();

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Please select a material type.");
        }

        type = type.trim().toUpperCase(Locale.ROOT);

        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid material type.");
        }

        String path = material.getResourcePath();

        if (path == null || path.isBlank()
                || path.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "A valid resource path or URL is required.");
        }

        // Check real assignment in the database.
        requireOfferingAccess(
                lecturerId, material.getOfferingId());

        // Do not trust uploader ID received from the UI.
        material.setUploadedBy(lecturerId);
        material.setTitle(title);
        material.setMaterialType(type);
        material.setResourcePath(path.trim());

        return materialsDAO.save(material);
    }

    public boolean delete(int materialId)
            throws SQLException {

        int lecturerId = requireLecturer();

        if (materialId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid material ID.");
        }

        return materialsDAO.delete(
                materialId, lecturerId);
    }
}
