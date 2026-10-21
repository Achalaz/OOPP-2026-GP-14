package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.ResultReportRow;
import java.sql.*;
import java.util.*;

public class ReportDAO {
    public record StudentOption(int studentId,String registrationNo,String studentName) {
        public String toString(){return registrationNo+" - "+studentName;}
    }
    public record OfferingOption(int offeringId,String courseCode,String courseName,int semesterId){
        public String toString(){return courseCode+" - "+courseName+" (semester "+semesterId+", offering "+offeringId+")";}
    }
    public record MarkDetail(String assessment,String type,Double mark,double maximum,double weight,String status) {}
    private static final String SCOPE="""
        AND (?=0 OR EXISTS(SELECT 1 FROM lecturer_course_assignments a WHERE a.offering_id=e.offering_id
             AND a.lecturer_id=? AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL')))
        """;
    public List<StudentOption> students(Connection c,int lecturer,int self)throws SQLException {
        var rows=new ArrayList<StudentOption>();
        try(var p=c.prepareStatement("""
            SELECT DISTINCT s.student_id,s.registration_no,CONCAT(u.first_name,' ',u.last_name)
            FROM students s JOIN users u ON u.user_id=s.student_id JOIN enrollments e ON e.student_id=s.student_id
            WHERE e.status<>'WITHDRAWN' AND (?=0 OR s.student_id=?)
            """+SCOPE+" ORDER BY s.registration_no")) {
            p.setInt(1,self);p.setInt(2,self);p.setInt(3,lecturer);p.setInt(4,lecturer);
            try(var r=p.executeQuery()){while(r.next())rows.add(new StudentOption(r.getInt(1),r.getString(2),r.getString(3)));}
        }return rows;
    }
    public List<OfferingOption> offerings(Connection c,int lecturer)throws SQLException {
        var rows=new ArrayList<OfferingOption>();
        try(var p=c.prepareStatement("""
            SELECT DISTINCT o.offering_id,c.course_code,c.course_name,o.semester_id FROM course_offerings o
            JOIN courses c ON c.course_id=o.course_id JOIN lecturer_course_assignments a ON a.offering_id=o.offering_id
            WHERE a.lecturer_id=? AND o.status<>'CANCELLED'
            AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL') ORDER BY o.semester_id,c.course_code,o.offering_id
            """)){
            p.setInt(1,lecturer);try(var r=p.executeQuery()){while(r.next())rows.add(new OfferingOption(r.getInt(1),r.getString(2),r.getString(3),r.getInt(4)));}
        }return rows;
    }
    public List<Integer> batches(Connection c,int lecturer)throws SQLException {
        var rows=new ArrayList<Integer>();
        try(var p=c.prepareStatement("""
            SELECT DISTINCT s.intake_year FROM students s JOIN enrollments e ON e.student_id=s.student_id
            WHERE e.status<>'WITHDRAWN'
            """+SCOPE+" ORDER BY s.intake_year")){
            p.setInt(1,lecturer);p.setInt(2,lecturer);try(var r=p.executeQuery()){while(r.next())rows.add(r.getInt(1));}
        }return rows;
    }
    /** LEFT JOIN hides all unpublished marks/grades while retaining pending enrollment counts. */
    public List<ResultReportRow> report(Connection c,int student,int offering,int intake,int lecturer)throws SQLException {
        var rows=new ArrayList<ResultReportRow>();
        try(var p=c.prepareStatement("""
            SELECT e.enrollment_id,s.registration_no,CONCAT(u.first_name,' ',u.last_name),c.course_code,c.course_name,
            c.credits,r.ca_mark,r.final_exam_mark,r.overall_mark,r.grade_letter,r.grade_point,
            COALESCE(r.result_status,'PENDING')
            FROM enrollments e JOIN students s ON s.student_id=e.student_id JOIN users u ON u.user_id=s.student_id
            JOIN course_offerings o ON o.offering_id=e.offering_id JOIN courses c ON c.course_id=o.course_id
            JOIN semesters sem ON sem.semester_id=o.semester_id JOIN academic_years ay ON ay.academic_year_id=sem.academic_year_id
            LEFT JOIN course_results r ON r.enrollment_id=e.enrollment_id AND r.published=TRUE
            WHERE e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
            AND (?=0 OR e.student_id=?) AND (?=0 OR e.offering_id=?) AND (?=0 OR s.intake_year=?)
            """+SCOPE+" ORDER BY s.registration_no,ay.start_date,ay.academic_year_name,sem.semester_no,c.course_code,e.attempt_no")) {
            p.setInt(1,student);p.setInt(2,student);p.setInt(3,offering);p.setInt(4,offering);
            p.setInt(5,intake);p.setInt(6,intake);p.setInt(7,lecturer);p.setInt(8,lecturer);
            try(var r=p.executeQuery()){while(r.next())rows.add(new ResultReportRow(r.getInt(1),r.getString(2),r.getString(3),
                r.getString(4),r.getString(5),r.getDouble(6),number(r,7),number(r,8),number(r,9),r.getString(10),number(r,11),r.getString(12)));}
        }return rows;
    }
    public List<MarkDetail> details(Connection c,int enrollment,int lecturer,int self)throws SQLException {
        var rows=new ArrayList<MarkDetail>();
        try(var p=c.prepareStatement("""
            SELECT a.assessment_name,a.assessment_type,m.marks_obtained,a.max_mark,a.weight_percentage,m.mark_status
            FROM enrollments e JOIN course_results r ON r.enrollment_id=e.enrollment_id AND r.published=TRUE
            JOIN assessments a ON a.offering_id=e.offering_id
            LEFT JOIN marks m ON m.assessment_id=a.assessment_id AND m.enrollment_id=e.enrollment_id
            WHERE e.enrollment_id=? AND (?=0 OR e.student_id=?)
            """+SCOPE+" ORDER BY a.assessment_id")){
            p.setInt(1,enrollment);p.setInt(2,self);p.setInt(3,self);p.setInt(4,lecturer);p.setInt(5,lecturer);
            try(var r=p.executeQuery()){while(r.next())rows.add(new MarkDetail(r.getString(1),r.getString(2),number(r,3),r.getDouble(4),r.getDouble(5),r.getString(6)));}
        }return rows;
    }
    private Double number(ResultSet r,int column)throws SQLException{return r.getObject(column)==null?null:r.getDouble(column);}
    public List<StudentOption> getStudents()throws SQLException{try(var c=DBConnection.getConnection()){return students(c,0,0);}}
    public List<OfferingOption> getCourseOfferings()throws SQLException{try(var c=DBConnection.getConnection()){return offerings(c,0);}}
    public List<ResultReportRow> getStudentResultReport(int student)throws SQLException{try(var c=DBConnection.getConnection()){return report(c,student,0,0,0);}}
    public List<ResultReportRow> getOfferingResultReport(int offering)throws SQLException{try(var c=DBConnection.getConnection()){return report(c,0,offering,0,0);}}
}