package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AssessmentDAO {

    public Double getMaxMarkById(int assessmentId) throws SQLException {

        String sql = """
                SELECT max_mark
                FROM assessments
                WHERE assessment_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, assessmentId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getDouble("max_mark");
                }
            }
        }

        return null;
    }
}