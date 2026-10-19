package lk.ac.ruhuna.ftms.dao;
import java.sql.*;
import java.util.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
public class LecturerCourseAssignmentDAO {
    public int enrollmentOffering(int enrollment) throws SQLException {
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("SELECT offering_id FROM enrollments WHERE enrollment_id=?")) {
            p.setInt(1,enrollment);
            try(var r=p.executeQuery()){if(r.next())return r.getInt(1);}
        }
        throw new SecurityException("Enrollment is unavailable.");
    }
    public boolean isAssigned(int lecturer, int offering) throws SQLException {
        try(var c=DBConnection.getConnection(); var p=c.prepareStatement(
                "SELECT 1 FROM lecturer_course_assignments WHERE lecturer_id=? AND offering_id=? AND assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL')")) {
            p.setInt(1,lecturer); p.setInt(2,offering);
            try(var r=p.executeQuery()) { return r.next(); }
        }
    }
    public List<EligibilityOffering> findAssigned(int lecturer) throws SQLException {
        var rows=new ArrayList<EligibilityOffering>();
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("""
            SELECT DISTINCT o.offering_id,c.course_code,c.course_name,o.offering_group
            FROM lecturer_course_assignments a JOIN course_offerings o ON o.offering_id=a.offering_id
            JOIN courses c ON c.course_id=o.course_id WHERE a.lecturer_id=? AND o.status<>'CANCELLED' AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL') ORDER BY c.course_code,o.offering_id
            """)) {
            p.setInt(1,lecturer);
            try(var r=p.executeQuery()) { while(r.next()) rows.add(new EligibilityOffering(r.getInt(1),r.getString(2),r.getString(3),r.getString(4))); }
        }
        return rows;
    }
    public int enrollmentOffering(int enrollment, int assessment, int attempt) throws SQLException {
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("""
            SELECT e.offering_id FROM enrollments e JOIN assessments a ON a.offering_id=e.offering_id
            WHERE e.enrollment_id=? AND a.assessment_id=? AND e.attempt_no=? AND e.status<>'WITHDRAWN'
            """)) {
            p.setInt(1,enrollment);p.setInt(2,assessment);p.setInt(3,attempt);
            try(var r=p.executeQuery()) { if(r.next()) return r.getInt(1); }
        }
        throw new SecurityException("Enrollment, assessment and attempt must match an assigned course.");
    }
}
