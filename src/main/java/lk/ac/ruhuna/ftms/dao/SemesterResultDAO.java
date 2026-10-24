package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.SemesterResult;
import java.sql.*;
import java.util.*;

public class SemesterResultDAO {
    public Set<Integer> studentIdsForOffering(int offering)throws SQLException {
        var ids=new HashSet<Integer>();
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("SELECT student_id FROM enrollments WHERE offering_id=? AND status<>'WITHDRAWN'")) {
            p.setInt(1,offering);try(var r=p.executeQuery()){while(r.next())ids.add(r.getInt(1));}
        }
        return ids;
    }
    public record GpaCourseRow(int enrollmentId,int semesterId,String courseCode,String courseName,double credits,
        String enrollmentStatus,String gradeLetter,Double gradePoint,String resultStatus,boolean passed,boolean published) {}
    public record StudentOption(int studentId,String registrationNo,String studentName) {
        public String toString(){return registrationNo+" - "+studentName;}
    }
    public record SemesterOption(int semesterId,String academicYear,int semesterNo,String semesterName) {
        public String toString(){return academicYear+" - "+(semesterName==null ? "Semester "+semesterNo : semesterName);}
    }
    public List<GpaCourseRow> getCourseRowsForSemester(int student,int semester)throws SQLException {
        try(var c=DBConnection.getConnection()){return getCourseRowsForSemester(c,student,semester);}
    }
    public List<GpaCourseRow> getCourseRowsForSemester(Connection c,int student,int semester)throws SQLException {
        var rows=new ArrayList<GpaCourseRow>();
        try(var p=c.prepareStatement("""
            SELECT e.enrollment_id,o.semester_id,c.course_code,c.course_name,c.credits,
            CASE WHEN e.attempt_no>1 THEN 'REPEAT' ELSE e.status END AS enrollment_status,
            r.grade_letter,r.grade_point,r.result_status,r.published
            FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
            JOIN courses c ON c.course_id=o.course_id
            LEFT JOIN course_results r ON r.enrollment_id=e.enrollment_id AND r.published=TRUE
            WHERE e.student_id=? AND o.semester_id=? AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
            ORDER BY c.course_code,e.attempt_no
            """)){
            p.setInt(1,student);p.setInt(2,semester);
            try(var r=p.executeQuery()){while(r.next())rows.add(new GpaCourseRow(r.getInt(1),r.getInt(2),
                r.getString(3),r.getString(4),r.getDouble(5),r.getString(6),r.getString(7),
                r.getObject(8)==null ? null : r.getDouble(8),r.getString(9),"PASS".equals(r.getString(9)),r.getBoolean(10)));}
        }
        return rows;
    }
    public List<SemesterOption> getSemestersForStudent(int student)throws SQLException{
        try(var c=DBConnection.getConnection()){return getSemestersForStudent(c,student);}
    }
    public List<SemesterOption> getSemestersForStudent(Connection c,int student)throws SQLException {
        var rows=new ArrayList<SemesterOption>();
        try(var p=c.prepareStatement("""
            SELECT DISTINCT s.semester_id,ay.academic_year_name,s.semester_no,s.semester_name,
            ay.start_date,s.start_date
            FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
            JOIN semesters s ON s.semester_id=o.semester_id
            JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
            WHERE e.student_id=? AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
            ORDER BY ay.start_date,ay.academic_year_name,s.semester_no,s.start_date,s.semester_id
            """)){
            p.setInt(1,student);try(var r=p.executeQuery()){while(r.next())rows.add(new SemesterOption(r.getInt(1),r.getString(2),r.getInt(3),r.getString(4)));}
        }return rows;
    }
    public List<Integer> getSemesterIdsByStudent(int student)throws SQLException {
        return getSemestersForStudent(student).stream().map(SemesterOption::semesterId).toList();
    }
    public List<StudentOption> getStudents()throws SQLException {
        try(var c=DBConnection.getConnection()){return getStudents(c);}
    }
    public List<StudentOption> getStudents(Connection c)throws SQLException {
        var rows=new ArrayList<StudentOption>();
        try(var p=c.prepareStatement("SELECT s.student_id,s.registration_no,CONCAT(u.first_name,' ',u.last_name) FROM students s JOIN users u ON u.user_id=s.student_id ORDER BY s.registration_no");var r=p.executeQuery()){
            while(r.next())rows.add(new StudentOption(r.getInt(1),r.getString(2),r.getString(3)));
        }return rows;
    }
    public void saveOrUpdateSemesterResult(SemesterResult result)throws SQLException {
        try(var c=DBConnection.getConnection()){saveOrUpdateSemesterResult(c,result);}
    }
    public void saveOrUpdateSemesterResult(Connection c,SemesterResult r)throws SQLException{
        try(var p=c.prepareStatement("""
            INSERT INTO semester_results(student_id,semester_id,total_registered_credits,total_earned_credits,
            total_quality_points,sgpa,cgpa) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
            total_registered_credits=VALUES(total_registered_credits),total_earned_credits=VALUES(total_earned_credits),
            total_quality_points=VALUES(total_quality_points),sgpa=VALUES(sgpa),cgpa=VALUES(cgpa),calculated_at=CURRENT_TIMESTAMP
            """)){
            p.setInt(1,r.getStudentId());p.setInt(2,r.getSemesterId());p.setDouble(3,r.getTotalRegisteredCredits());
            p.setDouble(4,r.getTotalEarnedCredits());p.setDouble(5,r.getTotalQualityPoints());
            p.setObject(6,r.getSgpa(),Types.DECIMAL);p.setObject(7,r.getCgpa(),Types.DECIMAL);p.executeUpdate();
        }
    }
    public SemesterResult getByStudentAndSemester(int student,int semester)throws SQLException {
        try(var c=DBConnection.getConnection()){return getByStudentAndSemester(c,student,semester);}
    }
    public SemesterResult getByStudentAndSemester(Connection c,int student,int semester)throws SQLException {
        try(var p=c.prepareStatement("SELECT * FROM semester_results WHERE student_id=? AND semester_id=?")) {
            p.setInt(1,student);p.setInt(2,semester);p.setQueryTimeout(15);
            try(var rows=p.executeQuery()){return rows.next()?map(rows):null;}
        }
    }
    public List<SemesterResult> getSemesterResultsByStudent(int student)throws SQLException {
        var rows=new ArrayList<SemesterResult>();
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("""
            SELECT r.* FROM semester_results r JOIN semesters s ON s.semester_id=r.semester_id
            JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
            WHERE r.student_id=? ORDER BY ay.start_date,ay.academic_year_name,s.semester_no,s.semester_id
            """)){
            p.setInt(1,student);try(var rs=p.executeQuery()){while(rs.next()){
                rows.add(map(rs));
            }}
        }return rows;
    }
    private SemesterResult map(ResultSet rs)throws SQLException {
        var r=new SemesterResult(rs.getInt("student_id"),rs.getInt("semester_id"),rs.getDouble("total_registered_credits"),
                rs.getDouble("total_earned_credits"),rs.getDouble("total_quality_points"),
                rs.getObject("sgpa")==null?null:rs.getDouble("sgpa"),rs.getObject("cgpa")==null?null:rs.getDouble("cgpa"));
        r.setSemesterResultId(rs.getInt("semester_result_id"));
        var time=rs.getTimestamp("calculated_at");r.setCalculatedAt(time==null?null:time.toLocalDateTime());return r;
    }
}
