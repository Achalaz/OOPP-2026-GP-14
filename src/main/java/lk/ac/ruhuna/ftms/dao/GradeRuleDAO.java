package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.GradeRule;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.List;

public class GradeRuleDAO {
    public List<GradeRule> findActiveRules() throws SQLException {
        String sql = "SELECT * FROM grade_rules WHERE is_active = 1 ORDER BY min_mark, grade_rule_id";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<GradeRule> rules = new ArrayList<>();
            while (rows.next()) {
                rules.add(map(rows));
            }
            return rules;
        }
    }

    /** Returns null for a gap. Overlapping active ranges are rejected, never guessed. */
    public GradeRule findMatchingRule(BigDecimal overallMark) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findMatchingRule(connection, overallMark);
        }
    }

    public GradeRule findMatchingRule(Connection connection, BigDecimal overallMark) throws SQLException {
        String sql = """
                SELECT * FROM grade_rules
                WHERE is_active = 1 AND min_mark <= ? AND max_mark >= ?
                ORDER BY grade_rule_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, overallMark);
            statement.setBigDecimal(2, overallMark);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                GradeRule rule = map(rows);
                if (rows.next()) {
                    throw new SQLDataException("Multiple active grade rules match " + overallMark
                            + ". Correct the overlapping ranges before calculating results.");
                }
                return rule;
            }
        }
    }

    private GradeRule map(ResultSet row) throws SQLException {
        return new GradeRule(row.getInt("grade_rule_id"), row.getString("grade_letter"),
                row.getBigDecimal("min_mark"), row.getBigDecimal("max_mark"),
                row.getBigDecimal("grade_point"), row.getBoolean("is_pass"), row.getBoolean("is_active"));
    }
}
