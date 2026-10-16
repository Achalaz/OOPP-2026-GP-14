
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Medical;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class MedicalDAO {

    // Save a new medical record and set its generated ID.
    public void save(Medical medical) throws SQLException {
        String sql = """
                INSERT INTO medicals
                    (student_id, medical_date, supporting_document_path,
                     status, recorded_by)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, medical.getStudentId());
            statement.setDate(2, Date.valueOf(medical.getMedicalDate()));
            statement.setString(3, medical.getSupportingDocumentPath());
            statement.setString(4, medical.getStatus());
            setNullableInt(statement, 5, medical.getRecordedBy());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    medical.setMedicalId(keys.getInt(1));
                }
            }
        }
    }

    // Update an existing medical record.
    public boolean update(Medical medical) throws SQLException {
        String sql = """
                UPDATE medicals
                SET student_id = ?,
                    medical_date = ?,
                    supporting_document_path = ?,
                    status = ?,
                    recorded_by = ?
                WHERE medical_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, medical.getStudentId());
            statement.setDate(2, Date.valueOf(medical.getMedicalDate()));
            statement.setString(3, medical.getSupportingDocumentPath());
            statement.setString(4, medical.getStatus());
            setNullableInt(statement, 5, medical.getRecordedBy());
            statement.setInt(6, medical.getMedicalId());

            return statement.executeUpdate() > 0;
        }
    }

    // Approve or reject a medical record.
    public boolean updateStatus(int medicalId, String status)
            throws SQLException {

        String sql = """
                UPDATE medicals
                SET status = ?
                WHERE medical_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setInt(2, medicalId);

            return statement.executeUpdate() > 0;
        }
    }

    // Find a medical by its ID.
    public Medical findById(int medicalId) throws SQLException {
        String sql = """
                SELECT medical_id, student_id, medical_date,
                       supporting_document_path, status, recorded_by
                FROM medicals
                WHERE medical_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        }
    }

    // Find all medical records for one student.
    public List<Medical> findByStudentId(int studentId)
            throws SQLException {

        String sql = """
                SELECT medical_id, student_id, medical_date,
                       supporting_document_path, status, recorded_by
                FROM medicals
                WHERE student_id = ?
                ORDER BY medical_date DESC, medical_id DESC
                """;

        List<Medical> medicals = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    medicals.add(mapRow(resultSet));
                }
            }
        }

        return medicals;
    }

    // Find all medical records.
    // Access permissions will be checked in the Service layer.
    public List<Medical> findAll() throws SQLException {
        String sql = """
                SELECT medical_id, student_id, medical_date,
                       supporting_document_path, status, recorded_by
                FROM medicals
                ORDER BY medical_date DESC, medical_id DESC
                """;

        List<Medical> medicals = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                medicals.add(mapRow(resultSet));
            }
        }

        return medicals;
    }

    // Delete a medical record.
    public boolean delete(int medicalId) throws SQLException {
        String sql = "DELETE FROM medicals WHERE medical_id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, medicalId);
            return statement.executeUpdate() > 0;
        }
    }

    // Handle nullable Technical Officer ID.
    private void setNullableInt(PreparedStatement statement,
                                int index, Integer value) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    // Convert a database row into a Medical object.
    private Medical mapRow(ResultSet resultSet) throws SQLException {

        Date date = resultSet.getDate("medical_date");

        return new Medical(
                resultSet.getInt("medical_id"),
                resultSet.getInt("student_id"),
                date == null ? null : date.toLocalDate(),
                resultSet.getString("supporting_document_path"),
                resultSet.getString("status"),
                (Integer) resultSet.getObject("recorded_by")
        );
    }
}
