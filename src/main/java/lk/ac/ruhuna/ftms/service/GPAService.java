package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.*;
import lk.ac.ruhuna.ftms.model.*;
import java.math.*;
import java.sql.*;
import java.util.*;

/** Published grade snapshots are authoritative; later grade-rule edits never rewrite historical GPA. */
public class GPAService {
    public record PublishedSemester(SemesterOption semester, SemesterResult result) {}

    /** Uses the existing GPA rules and published snapshots, without saving or exposing draft-only semesters. */
    public List<PublishedSemester> getMyPublishedGpa(lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity identity)
            throws SQLException {
        return new StudentAccessService(identity).read((connection, student) -> {
            var semesters = new ArrayList<PublishedSemester>();
            for (var semester : dao.getSemestersForStudent(connection, student)) {
                if (dao.getCourseRowsForSemester(connection, student, semester.semesterId()).stream()
                        .anyMatch(GpaCourseRow::published)) {
                    semesters.add(new PublishedSemester(semester, calculate(connection, student, semester.semesterId())));
                }
            }
            return List.copyOf(semesters);
        });
    }
    private final SemesterResultDAO dao=new SemesterResultDAO();
    private final AcademicAccessDAO access=new AcademicAccessDAO();

    public record Totals(double registered,double earned,double quality,Double gpa) {}
    public static Double qualityPoints(GpaCourseRow row) {
        if(row==null || !row.published() || row.gradePoint()==null || "WITHDRAWN".equals(row.enrollmentStatus())
            || !List.of("PASS","FAIL").contains(Objects.toString(row.resultStatus(),""))) return null;
        return BigDecimal.valueOf(row.credits()).multiply(BigDecimal.valueOf(row.gradePoint())).doubleValue();
    }
    public static Totals totals(List<GpaCourseRow> rows) {
        BigDecimal registered=BigDecimal.ZERO,earned=BigDecimal.ZERO,quality=BigDecimal.ZERO,applicable=BigDecimal.ZERO;
        Set<String> courses=new HashSet<>(); boolean repeat=false;
        for(var row:rows) {
            if("WITHDRAWN".equals(row.enrollmentStatus()))continue;
            if(!Double.isFinite(row.credits()) || row.credits()<0)throw new IllegalArgumentException("Invalid course credits.");
            BigDecimal credit=BigDecimal.valueOf(row.credits());registered=registered.add(credit);
            if(!courses.add(row.courseCode()) || "REPEAT".equals(row.enrollmentStatus()))repeat=true;
            if(!row.published() || row.gradePoint()==null || !List.of("PASS","FAIL").contains(Objects.toString(row.resultStatus(),"")))continue;
            if(!Double.isFinite(row.gradePoint()) || row.gradePoint()<0)throw new IllegalArgumentException("Invalid grade point.");
            applicable=applicable.add(credit);quality=quality.add(credit.multiply(BigDecimal.valueOf(row.gradePoint())));
            if("PASS".equals(row.resultStatus()))earned=earned.add(credit);
        }
        Double gpa=repeat || applicable.signum()==0 ? null : quality.divide(applicable,2,RoundingMode.HALF_UP).doubleValue();
        return new Totals(registered.doubleValue(),earned.doubleValue(),quality.doubleValue(),gpa);
    }
    private SemesterResult calculate(Connection c,int student,int semester)throws SQLException {
        List<GpaCourseRow> cumulative=new ArrayList<>();List<GpaCourseRow> current=null;
        for(var s:dao.getSemestersForStudent(c,student)){
            var rows=dao.getCourseRowsForSemester(c,student,s.semesterId());cumulative.addAll(rows);
            if(s.semesterId()==semester){current=rows;break;}
        }
        if(current==null)throw new IllegalArgumentException("No enrollment in the selected semester.");
        var now=totals(current);var all=totals(cumulative);
        return new SemesterResult(student,semester,now.registered(),now.earned(),now.quality(),now.gpa(),all.gpa());
    }
    public SemesterResult calculateSemesterGPA(int student,int semester)throws SQLException {
        return AcademicTransaction.run((c,id)->{access.transcript(c,id,student,false);return calculate(c,student,semester);});
    }
    public Double calculateCGPA(int student,int semester)throws SQLException{return calculateSemesterGPA(student,semester).getCgpa();}
    /** Recalculates every semester atomically; supplied totals are ignored, including forged or stale values. */
    public void saveSemesterResult(SemesterResult requested)throws SQLException {
        if(requested==null)throw new IllegalArgumentException("Select a student and semester.");
        AcademicTransaction.run((c,id)->{
            access.transcript(c,id,requested.getStudentId(),true);
            calculate(c,requested.getStudentId(),requested.getSemesterId());
            for(var s:dao.getSemestersForStudent(c,requested.getStudentId()))
                dao.saveOrUpdateSemesterResult(c,calculate(c,requested.getStudentId(),s.semesterId()));
            return null;
        });
    }
    /** Views calculate current published data so publishing cannot leave a stale cached GPA on screen. */
    public SemesterResult getSemesterResult(int student,int semester)throws SQLException{return calculateSemesterGPA(student,semester);}
    public List<GpaCourseRow> getCourseRows(int student,int semester)throws SQLException {
        return AcademicTransaction.run((c,id)->{access.transcript(c,id,student,false);return dao.getCourseRowsForSemester(c,student,semester);});
    }
    public List<SemesterResult> getSemesterResultsByStudent(int student)throws SQLException {
        return AcademicTransaction.run((c,id)->{
            access.transcript(c,id,student,false);var rows=new ArrayList<SemesterResult>();
            for(var s:dao.getSemestersForStudent(c,student))rows.add(calculate(c,student,s.semesterId()));
            return rows;
        });
    }
    public List<StudentOption> getStudents()throws SQLException {
        return AcademicTransaction.run((c,id)->{
            var actor=access.actor(c,id);var rows=new ArrayList<StudentOption>();
            if(!List.of("LECTURER","STUDENT").contains(actor.getRole()))throw new SecurityException("Academic access denied.");
            for(var student:dao.getStudents(c)) {
                try {access.transcript(c,id,student.studentId(),false);rows.add(student);}
                catch(SecurityException|IllegalArgumentException denied){/* Exclude inaccessible transcripts. */}
            }return rows;
        });
    }
    public List<SemesterOption> getSemestersForStudent(int student)throws SQLException {
        return AcademicTransaction.run((c,id)->{access.transcript(c,id,student,false);return dao.getSemestersForStudent(c,student);});
    }
    public LecturerTable getLecturerGpa(int offering)throws SQLException {
        new LecturerAccessService().requireOffering(offering);
        // Keep the existing workspace API, using current GPA and the same transcript guard.
        var result=new ArrayList<List<String>>();
        var studentIds=dao.studentIdsForOffering(offering);
        for(var student:getStudents())if(studentIds.contains(student.studentId()))for(var r:getSemesterResultsByStudent(student.studentId()))
            result.add(List.of(student.registrationNo(),Integer.toString(r.getSemesterId()),Objects.toString(r.getSgpa(),"Pending"),Objects.toString(r.getCgpa(),"Pending")));
        return new LecturerTable(List.of("Registration","Semester","SGPA","CGPA"),result);
    }
}
