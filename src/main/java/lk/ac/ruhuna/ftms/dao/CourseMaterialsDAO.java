package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseMaterials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CourseMaterialsDAO {

    private static final String TABLE_NAME = "course_materials";

    private static final Set<String> VALID_MATERIAL_TYPES = Set.of(
            "LECTURE_NOTE",
            "PRACTICAL",
            "ASSIGNMENT",
            "LINK",
            "OTHER"
    );

    // Retrieve all materials uploaded by a particular user.
    public List<CourseMaterials> getByUploader(int uploadedBy)
            throws SQLException {

        if (uploadedBy <= 0) {
            throw new IllegalArgumentException(
                    "Invalid uploader ID.");
        }

        String sql =
                "SELECT material_id, offering_id, title, description, "
                        + "material_type, resource_path, uploaded_by, uploaded_at "
                        + "FROM " + TABLE_NAME + " "
                        + "WHERE uploaded_by = ? "
                        + "ORDER BY uploaded_at DESC, material_id DESC";

        List<CourseMaterials> materials = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, uploadedBy);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    materials.add(mapMaterial(resultSet));
                }
            }
        }

        return materials;
    }

    // Search the logged-in user's materials by title or description.
    public List<CourseMaterials> searchByUploader(
            int uploadedBy,
            String keyword) throws SQLException {

        if (uploadedBy <= 0) {
            throw new IllegalArgumentException(
                    "Invalid uploader ID.");
        }

        if (keyword == null || keyword.isBlank()) {
            return getByUploader(uploadedBy);
        }

        String sql =
                "SELECT material_id, offering_id, title, description, "
                        + "material_type, resource_path, uploaded_by, uploaded_at "
                        + "FROM " + TABLE_NAME + " "
                        + "WHERE uploaded_by = ? "
                        + "AND (title LIKE ? OR description LIKE ?) "
                        + "ORDER BY uploaded_at DESC, material_id DESC";

        List<CourseMaterials> materials = new ArrayList<>();

        String searchKeyword = "%" + keyword.trim() + "%";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, uploadedBy);
            statement.setString(2, searchKeyword);
            statement.setString(3, searchKeyword);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    materials.add(mapMaterial(resultSet));
                }
            }
        }

        return materials;
    }

    // Insert a new material or update an existing one.
    public boolean save(CourseMaterials material)
            throws SQLException {

        validateMaterial(material);

        if (material.getMaterialId() <= 0) {
            return insert(material);
        }

        return update(material);
    }

    // Insert a new course material.
    private boolean insert(CourseMaterials material)
            throws SQLException {

        String sql =
                "INSERT INTO " + TABLE_NAME + " "
                        + "(offering_id, title, description, material_type, "
                        + "resource_path, uploaded_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, material.getOfferingId());
            statement.setString(2, material.getTitle());
            statement.setString(3, material.getDescription());
            statement.setString(4, material.getMaterialType());
            statement.setString(5, normalizePath(
                    material.getResourcePath()));
            statement.setInt(6, material.getUploadedBy());

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                return false;
            }

            try (ResultSet generatedKeys =
                         statement.getGeneratedKeys()) {

                if (generatedKeys.next()) {
                    material.setMaterialId(
                            generatedKeys.getInt(1));
                }
            }

            return true;
        }
    }

    // Update only a material owned by the specified uploader.
    private boolean update(CourseMaterials material)
            throws SQLException {

        String sql =
                "UPDATE " + TABLE_NAME + " "
                        + "SET offering_id = ?, title = ?, description = ?, "
                        + "material_type = ?, resource_path = ? "
                        + "WHERE material_id = ? AND uploaded_by = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, material.getOfferingId());
            statement.setString(2, material.getTitle());
            statement.setString(3, material.getDescription());
            statement.setString(4, material.getMaterialType());
            statement.setString(5, normalizePath(
                    material.getResourcePath()));
            statement.setInt(6, material.getMaterialId());
            statement.setInt(7, material.getUploadedBy());

            return statement.executeUpdate() > 0;
        }
    }

    // Delete only a material owned by the specified uploader.
    public boolean delete(int materialId, int uploadedBy)
            throws SQLException {

        if (materialId <= 0 || uploadedBy <= 0) {
            throw new IllegalArgumentException(
                    "Invalid material ID or uploader ID.");
        }

        String sql =
                "DELETE FROM " + TABLE_NAME + " "
                        + "WHERE material_id = ? AND uploaded_by = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, materialId);
            statement.setInt(2, uploadedBy);

            return statement.executeUpdate() > 0;
        }
    }

    // Validate material data before inserting or updating.
    private void validateMaterial(CourseMaterials material) {

        if (material == null) {
            throw new IllegalArgumentException(
                    "Material cannot be null.");
        }

        if (material.getOfferingId() <= 0) {
            throw new IllegalArgumentException(
                    "Please select a valid course offering.");
        }

        if (material.getUploadedBy() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid uploader ID.");
        }

        String title = material.getTitle();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Material title is required.");
        }

        title = title.trim();

        if (title.length() > 180) {
            throw new IllegalArgumentException(
                    "Title cannot exceed 180 characters.");
        }

        material.setTitle(title);

        String type = material.getMaterialType();

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Material type is required.");
        }

        type = type.trim().toUpperCase(Locale.ROOT);

        if (!VALID_MATERIAL_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid material type. Use LECTURE_NOTE, "
                            + "PRACTICAL, ASSIGNMENT, LINK or OTHER.");
        }

        material.setMaterialType(type);

        String path = material.getResourcePath();

        if (path != null) {
            path = path.trim();

            if (path.length() > 500) {
                throw new IllegalArgumentException(
                        "Resource path cannot exceed 500 characters.");
            }

            material.setResourcePath(
                    path.isEmpty() ? null : path);
        }
    }

    // Convert an empty resource path into SQL NULL.
    private String normalizePath(String path) {

        if (path == null || path.isBlank()) {
            return null;
        }

        return path.trim();
    }

    // Map a database row into a CourseMaterials object.
    private CourseMaterials mapMaterial(ResultSet resultSet)
            throws SQLException {

        CourseMaterials material = new CourseMaterials();

        material.setMaterialId(
                resultSet.getInt("material_id"));

        material.setOfferingId(
                resultSet.getInt("offering_id"));

        material.setTitle(
                resultSet.getString("title"));

        material.setDescription(
                resultSet.getString("description"));

        material.setMaterialType(
                resultSet.getString("material_type"));

        material.setResourcePath(
                resultSet.getString("resource_path"));

        material.setUploadedBy(
                resultSet.getInt("uploaded_by"));

        Timestamp uploadedAt =
                resultSet.getTimestamp("uploaded_at");

        if (uploadedAt != null) {
            material.setUploadedAt(
                    uploadedAt.toLocalDateTime());
        }

        return material;
    }
}
