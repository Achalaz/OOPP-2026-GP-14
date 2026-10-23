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

public class CourseMaterialsDAO {

    // Get materials uploaded by a specific lecturer
    public List<CourseMaterials> getByUploader(int uploadedBy)
            throws SQLException {

        String sql = """
                SELECT material_id, offering_id, title, description,
                       material_type, resource_path, uploaded_by,
                       uploaded_at
                FROM course_materials
                WHERE uploaded_by = ?
                ORDER BY uploaded_at DESC
                """;

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

    // Search materials by title or description
    public List<CourseMaterials> searchByUploader(
            int uploadedBy, String keyword) throws SQLException {

        if (keyword == null || keyword.isBlank()) {
            return getByUploader(uploadedBy);
        }

        String sql = """
                SELECT material_id, offering_id, title, description,
                       material_type, resource_path, uploaded_by,
                       uploaded_at
                FROM course_materials
                WHERE uploaded_by = ?
                  AND (title LIKE ? OR description LIKE ?)
                ORDER BY uploaded_at DESC
                """;

        List<CourseMaterials> materials = new ArrayList<>();
        String searchValue = "%" + keyword.trim() + "%";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, uploadedBy);
            statement.setString(2, searchValue);
            statement.setString(3, searchValue);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    materials.add(mapMaterial(resultSet));
                }
            }
        }

        return materials;
    }

    // Insert a new material or update an existing one
    public boolean save(CourseMaterials material)
            throws SQLException {

        if (material == null) {
            throw new IllegalArgumentException(
                    "Material cannot be null.");
        }

        if (material.getTitle() == null
                || material.getTitle().isBlank()
                || material.getTitle().trim().length() > 180) {
            throw new IllegalArgumentException(
                    "Title is required and must not exceed 180 characters.");
        }

        if (material.getOfferingId() <= 0
                || material.getUploadedBy() <= 0) {
            throw new IllegalArgumentException(
                    "Valid offering and uploader IDs are required.");
        }

        if (material.getMaterialType() == null
                || material.getMaterialType().isBlank()) {
            throw new IllegalArgumentException(
                    "Material type is required.");
        }

        if (material.getMaterialId() > 0) {
            return update(material);
        }

        String sql = """
                INSERT INTO course_materials
                    (offering_id, title, description, material_type,
                     resource_path, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, material.getOfferingId());
            statement.setString(2, material.getTitle().trim());
            statement.setString(3, material.getDescription());
            statement.setString(4, material.getMaterialType());
            statement.setString(5, material.getResourcePath());
            statement.setInt(6, material.getUploadedBy());

            if (statement.executeUpdate() == 0) {
                return false;
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    material.setMaterialId(keys.getInt(1));
                }
            }

            return material.getMaterialId() > 0;
        }
    }

    // Update only a material owned by the specified uploader
    private boolean update(CourseMaterials material)
            throws SQLException {

        String sql = """
                UPDATE course_materials
                SET offering_id = ?,
                    title = ?,
                    description = ?,
                    material_type = ?,
                    resource_path = ?
                WHERE material_id = ?
                  AND uploaded_by = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, material.getOfferingId());
            statement.setString(2, material.getTitle().trim());
            statement.setString(3, material.getDescription());
            statement.setString(4, material.getMaterialType());
            statement.setString(5, material.getResourcePath());
            statement.setInt(6, material.getMaterialId());
            statement.setInt(7, material.getUploadedBy());

            return statement.executeUpdate() > 0;
        }
    }

    // Delete only a material owned by the specified uploader
    public boolean delete(int materialId, int uploadedBy)
            throws SQLException {

        if (materialId <= 0 || uploadedBy <= 0) {
            throw new IllegalArgumentException(
                    "Valid material and uploader IDs are required.");
        }

        String sql = """
                DELETE FROM course_materials
                WHERE material_id = ?
                  AND uploaded_by = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, materialId);
            statement.setInt(2, uploadedBy);

            return statement.executeUpdate() > 0;
        }
    }

    // Convert a database row into a CourseMaterials object
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
