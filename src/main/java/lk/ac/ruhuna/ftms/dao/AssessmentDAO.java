package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Assessment;
import java.sql.*;
import java.util.*;

public class AssessmentDAO {
    public Double getMaxMarkById(int id) throws SQLException {
        try (var c = DBConnection.getConnection(); var p = c.prepareStatement("SELECT max_mark FROM assessments WHERE assessment_id=?")) {
            p.setInt(1,id);
            try(var r=p.executeQuery()) { return r.next() ? r.getDouble(1) : null; }
        }
    }
    public List<Assessment> findByOffering(Connection c, int offering) throws SQLException {
        var rows = new ArrayList<Assessment>();
        try(var p=c.prepareStatement("SELECT * FROM assessments WHERE offering_id=? ORDER BY assessment_id")) {
            p.setInt(1,offering);
            try(var r=p.executeQuery()) { while(r.next()) rows.add(map(r)); }
        }
        return rows;
    }
    /** Defense in depth: publication and enrollment predicates are enforced in SQL as well as the service. */
    public List<Assessment> findReleasedForStudent(Connection c, int student, int offering) throws SQLException {
        var rows = new ArrayList<Assessment>();
        try (var p = c.prepareStatement("""
                SELECT a.* FROM assessments a
                WHERE a.offering_id=? AND a.is_published=TRUE
                  AND EXISTS (SELECT 1 FROM enrollments e
                      JOIN course_offerings o ON o.offering_id=e.offering_id
                      WHERE e.offering_id=a.offering_id AND e.student_id=?
                        AND e.status IN ('ENROLLED','REPEAT','COMPLETED')
                        AND o.status IN ('PLANNED','ACTIVE','COMPLETED'))
                ORDER BY a.assessment_date,a.assessment_id
                """)) {
            p.setInt(1, offering); p.setInt(2, student); p.setQueryTimeout(15);
            try (var r = p.executeQuery()) { while (r.next()) rows.add(map(r)); }
        }
        return List.copyOf(rows);
    }

    /** Service must authorize the lecturer and lock the offering in this same transaction. */
    public void release(Connection c, int offering, int assessment) throws SQLException {
        if (c.getAutoCommit()) throw new SQLException("Assessment release requires a transaction.");
        try (var p = c.prepareStatement("""
                UPDATE assessments SET is_published=TRUE,published_at=CURRENT_TIMESTAMP
                WHERE offering_id=? AND assessment_id=? AND is_published=FALSE AND published_at IS NULL
                """)) {
            p.setInt(1, offering); p.setInt(2, assessment); p.setQueryTimeout(15);
            if (p.executeUpdate() != 1)
                throw new IllegalStateException("Assessment is already released or unavailable. Refresh the list.");
        }
    }

    private Assessment map(ResultSet r) throws SQLException {
        var a = new Assessment(r.getInt("assessment_id"),r.getInt("offering_id"),
                r.getString("assessment_name"),r.getString("assessment_type"),r.getDouble("max_mark"),
                r.getDouble("weight_percentage"),r.getObject("assessment_date",java.time.LocalDate.class),r.getString("description"));
        a.setPublished(r.getBoolean("is_published"));
        var publishedAt = r.getTimestamp("published_at");
        a.setPublishedAt(publishedAt == null ? null : publishedAt.toLocalDateTime());
        return a;
    }
    public boolean hasMarks(Connection c, int assessment) throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM marks WHERE assessment_id=? LIMIT 1")) {
            p.setInt(1,assessment); try(var r=p.executeQuery()){return r.next();}
        }
    }
    public int save(Connection c, Assessment a, int actor) throws SQLException {
        boolean add=a.getAssessmentId()==0;
        String sql=add ? """
            INSERT INTO assessments(assessment_name,assessment_type,max_mark,weight_percentage,
            assessment_date,description,offering_id,created_by,is_published,published_at) VALUES(?,?,?,?,?,?,?,?,FALSE,NULL)
            """ : """
            UPDATE assessments SET assessment_name=?,assessment_type=?,max_mark=?,weight_percentage=?,
            assessment_date=?,description=? WHERE offering_id=? AND assessment_id=? AND is_published=FALSE AND published_at IS NULL
            """;
        try(var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,a.getAssessmentName().trim()); p.setString(2,a.getAssessmentType());
            p.setDouble(3,a.getMaxMark()); p.setDouble(4,a.getWeightPercentage());
            p.setObject(5,a.getAssessmentDate()); p.setString(6,a.getDescription());
            p.setInt(7,a.getOfferingId()); p.setInt(8,add ? actor : a.getAssessmentId());
            if(p.executeUpdate()!=1) throw new SQLException("Assessment changed or is unavailable.");
            if(!add)return a.getAssessmentId();
            try(var r=p.getGeneratedKeys()){if(r.next()) return r.getInt(1);}
            throw new SQLException("No assessment identifier returned.");
        }
    }
    public void delete(Connection c,int offering,int id) throws SQLException {
        try(var p=c.prepareStatement("DELETE FROM assessments WHERE offering_id=? AND assessment_id=? AND is_published=FALSE AND published_at IS NULL")) {
            p.setInt(1,offering);p.setInt(2,id);
            if(p.executeUpdate()!=1)throw new IllegalArgumentException("Assessment is unavailable.");
        }
    }
}
