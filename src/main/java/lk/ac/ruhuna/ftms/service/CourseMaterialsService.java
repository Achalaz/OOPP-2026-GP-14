package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseMaterialsDAO;
import lk.ac.ruhuna.ftms.model.CourseMaterials;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class CourseMaterialsService {

    private final CourseMaterialsDAO materialsDAO;

    // Checks whether a lecturer is assigned to an offering.
    private final BiPredicate<Integer, Integer> canManageOffering;

    public CourseMaterialsService(
            CourseMaterialsDAO materialsDAO,
            BiPredicate<Integer, Integer> canManageOffering) {

        this.materialsDAO =
                Objects.requireNonNull(materialsDAO);

        this.canManageOffering =
                Objects.requireNonNull(canManageOffering);
    }

    // Verify login and lecturer role.
    private int requireLecturer() {

        SessionManager.SessionIdentity identity =
                SessionManager.requireSession();

        User user = SessionManager.getCurrentUser();

        // Ensure the same login session is still active.
        SessionManager.requireSession(identity);

        if (user == null
                || !"LECTURER".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException(
                    "Access denied. Lecturers only.");
        }

        return identity.userId();
    }

    // Verify access to a course offering.
    private void requireOfferingAccess(
            int lecturerId, int offeringId) {

        if (offeringId <= 0
                || !canManageOffering.test(
                lecturerId, offeringId)) {

            throw new SecurityException(
                    "You are not assigned to this course offering.");
        }
    }

    // Retrieve the logged-in lecturer's materials.
    public List<CourseMaterials> getMyMaterials()
            throws SQLException {

        int lecturerId = requireLecturer();

        return materialsDAO.getByUploader(lecturerId);
    }

    // Search the logged-in lecturer's materials.
    public List<CourseMaterials> searchMyMaterials(
            String keyword) throws SQLException {

        int lecturerId = requireLecturer();

        return materialsDAO.searchByUploader(
                lecturerId, keyword);
    }

    // Create or update a material.
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

        if (material.getMaterialType() == null
                || material.getMaterialType().isBlank()) {
            throw new IllegalArgumentException(
                    "Please select a material type.");
        }

        String path = material.getResourcePath();

        if (path == null || path.isBlank()
                || path.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "A valid file path is required (maximum 500 characters).");
        }

        // Validate access to the selected course offering.
        requireOfferingAccess(
                lecturerId, material.getOfferingId());

        // Never trust the uploader ID supplied by the UI.
        material.setUploadedBy(lecturerId);
        material.setTitle(title);
        material.setResourcePath(path.trim());

        return materialsDAO.save(material);
    }

    // Delete only the logged-in lecturer's own material.
    public boolean delete(int materialId)
            throws SQLException {

        int lecturerId = requireLecturer();

        return materialsDAO.delete(
                materialId, lecturerId);
    }
}
