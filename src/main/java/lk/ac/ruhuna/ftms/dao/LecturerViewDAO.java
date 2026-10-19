package lk.ac.ruhuna.ftms.dao;
import java.sql.*;
import java.util.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.LecturerTable;
public class LecturerViewDAO {
    public LecturerTable students(int offering) throws SQLException {
        return query("""
            SELECT e.enrollment_id AS Enrollment,s.registration_no AS Registration,
            CONCAT(u.first_name,' ',u.last_name) AS Name,u.email AS Email,
            s.intake_year AS Intake,s.current_level AS Level,e.attempt_no AS Attempt,e.status AS Status
            FROM enrollments e JOIN students s ON s.student_id=e.student_id
            JOIN users u ON u.user_id=s.student_id WHERE e.offering_id=? ORDER BY s.registration_no
            """, offering);
    }
    public LecturerTable marks(int offering) throws SQLException {
        return query("""
            SELECT e.enrollment_id AS Enrollment,s.registration_no AS Registration,
            a.assessment_id AS Assessment,a.assessment_name AS Name,a.assessment_type AS Type,
            a.max_mark AS Maximum,e.attempt_no AS Attempt,m.marks_obtained AS Mark,m.mark_status AS Status
            FROM enrollments e JOIN students s ON s.student_id=e.student_id
            JOIN assessments a ON a.offering_id=e.offering_id
            LEFT JOIN marks m ON m.enrollment_id=e.enrollment_id AND m.assessment_id=a.assessment_id
            WHERE e.offering_id=? AND e.status<>'WITHDRAWN' ORDER BY s.registration_no,a.assessment_id
            """,offering);
    }
    public LecturerTable medicals(int offering) throws SQLException {
        return query("""
            SELECT s.registration_no AS Registration,m.from_date AS From_date,m.to_date AS To_date,m.status AS Status
            FROM medicals m JOIN students s ON s.student_id=m.student_id
            WHERE m.offering_id=? AND EXISTS(SELECT 1 FROM enrollments e
                WHERE e.student_id=m.student_id AND e.offering_id=m.offering_id)
            ORDER BY s.registration_no,m.from_date
            """,offering);
    }
    /** Saved cumulative totals for students in the authorized offering. Withhold totals
     * if any underlying result is missing, unpublished, or newer than the saved GPA. */
    public LecturerTable gpa(int offering) throws SQLException {
        return query("""
            SELECT DISTINCT s.registration_no AS Registration,sr.semester_id AS Semester,
            sr.sgpa AS SGPA,sr.cgpa AS CGPA,sr.calculated_at AS Calculated
            FROM enrollments selected JOIN students s ON s.student_id=selected.student_id
            JOIN semester_results sr ON sr.student_id=s.student_id
            WHERE selected.offering_id=? AND NOT EXISTS (
                SELECT 1 FROM enrollments e LEFT JOIN course_results cr ON cr.enrollment_id=e.enrollment_id
                WHERE e.student_id=s.student_id AND e.status<>'WITHDRAWN'
                AND (cr.result_id IS NULL OR cr.published=FALSE OR cr.calculated_at>sr.calculated_at))
            ORDER BY s.registration_no,sr.semester_id
            """,offering);
    }
    private LecturerTable query(String sql,int... ids) throws SQLException {
        try(var c=DBConnection.getConnection();var p=c.prepareStatement(sql)) {
            p.setQueryTimeout(15);
            for(int i=0;i<ids.length;i++) p.setInt(i+1,ids[i]);
            try(var r=p.executeQuery()) {
                var cols=new ArrayList<String>();var rows=new ArrayList<List<String>>();
                int n=r.getMetaData().getColumnCount();
                for(int i=1;i<=n;i++) cols.add(r.getMetaData().getColumnLabel(i));
                while(r.next()) {var row=new ArrayList<String>();for(int i=1;i<=n;i++) row.add(Objects.toString(r.getObject(i),"—"));rows.add(List.copyOf(row));}
                return new LecturerTable(List.copyOf(cols),List.copyOf(rows));
            }
        }
    }
}
