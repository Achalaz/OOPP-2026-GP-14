package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.*;
import lk.ac.ruhuna.ftms.model.*;
import java.math.*;
import java.sql.*;
import java.util.*;

/** Published grade snapshots are authoritative; later grade-rule edits never rewrite historical GPA. */
public class GPAService {
    public record PublishedSemester(SemesterOption semester, SemesterResult result, String status) {
        public PublishedSemester(SemesterOption semester, SemesterResult result) { this(semester,result,""); }
    }
    public record SemesterView(List<GpaCourseRow> rows, SemesterResult result, boolean savedCurrent,
                               double includedCredits, int excludedCourses, Set<String> repeatedCourses, String message) {
        public SemesterView { rows=List.copyOf(rows); repeatedCourses=Set.copyOf(repeatedCourses); }
    }

    /** Uses the existing GPA rules and published snapshots, without saving or exposing draft-only semesters. */
    public List<PublishedSemester> getMyPublishedGpa(lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity identity)
            throws SQLException {
        return new StudentAccessService(identity).readSnapshot(this::getPublishedGpa);
    }

    /** Called only within the authenticated student's read transaction. */
    List<PublishedSemester> getPublishedGpa(Connection connection, int student) throws SQLException {
        var semesters = new ArrayList<PublishedSemester>();
        for (var semester : dao.getSemestersForStudent(connection, student)) {
            if (dao.getCourseRowsForSemester(connection, student, semester.semesterId()).stream()
                    .anyMatch(GpaCourseRow::published)) {
                var view=semesterView(connection,student,semester.semesterId(),true);
                semesters.add(new PublishedSemester(semester,view.result(),view.message()));
            }
        }
        return List.copyOf(semesters);
    }
    private final SemesterResultDAO dao=new SemesterResultDAO();
    private final AcademicAccessDAO access=new AcademicAccessDAO();

    public record Totals(double registered,double earned,double quality,Double gpa,
                         double includedCredits,int excludedCourses,boolean repeatPending) {}
    public static Double qualityPoints(GpaCourseRow row) {
        if(row==null || !row.published() || row.gradePoint()==null || List.of("WITHDRAWN","REPEAT").contains(row.enrollmentStatus())
            || !List.of("PASS","FAIL").contains(Objects.toString(row.resultStatus(),""))) return null;
        return BigDecimal.valueOf(row.credits()).multiply(BigDecimal.valueOf(row.gradePoint())).doubleValue();
    }
    public static Totals totals(List<GpaCourseRow> rows) {
        return totals(rows,repeatedCourses(rows));
    }
    private static Set<String> repeatedCourses(List<GpaCourseRow> rows) {
        Set<String> seen=new HashSet<>(),repeated=new HashSet<>();
        for(var row:rows) if(!"WITHDRAWN".equals(row.enrollmentStatus()))
            if(!seen.add(row.courseCode()) || "REPEAT".equals(row.enrollmentStatus())) repeated.add(row.courseCode());
        return repeated;
    }
    private static Totals totals(List<GpaCourseRow> rows,Set<String> repeated) {
        BigDecimal registered=BigDecimal.ZERO,earned=BigDecimal.ZERO,quality=BigDecimal.ZERO,applicable=BigDecimal.ZERO;
        Set<String> courses=new HashSet<>(); boolean repeat=false;int excluded=0;
        for(var row:rows) {
            if("WITHDRAWN".equals(row.enrollmentStatus()))continue;
            if(!Double.isFinite(row.credits()) || row.credits()<0)throw new IllegalArgumentException("Invalid course credits.");
            BigDecimal credit=BigDecimal.valueOf(row.credits());
            if(courses.add(row.courseCode())) registered=registered.add(credit);
            if(repeated.contains(row.courseCode())) {repeat=true;excluded++;continue;}
            if(!row.published() || row.gradePoint()==null || !List.of("PASS","FAIL").contains(Objects.toString(row.resultStatus(),""))) {excluded++;continue;}
            if(!Double.isFinite(row.gradePoint()) || row.gradePoint()<0)throw new IllegalArgumentException("Invalid grade point.");
            applicable=applicable.add(credit);quality=quality.add(credit.multiply(BigDecimal.valueOf(row.gradePoint())));
            if("PASS".equals(row.resultStatus()))earned=earned.add(credit);
        }
        Double gpa=repeat || applicable.signum()==0 ? null : quality.divide(applicable,2,RoundingMode.HALF_UP).doubleValue();
        return new Totals(registered.setScale(2,RoundingMode.HALF_UP).doubleValue(),earned.setScale(2,RoundingMode.HALF_UP).doubleValue(),
                quality.setScale(2,RoundingMode.HALF_UP).doubleValue(),gpa,applicable.doubleValue(),excluded,repeat);
    }
    private SemesterResult calculate(Connection c,int student,int semester)throws SQLException {
        List<GpaCourseRow> cumulative=new ArrayList<>();List<GpaCourseRow> current=null;
        for(var s:dao.getSemestersForStudent(c,student)){
            var rows=dao.getCourseRowsForSemester(c,student,s.semesterId());cumulative.addAll(rows);
            if(s.semesterId()==semester){current=rows;break;}
        }
        if(current==null)throw new IllegalArgumentException("No enrollment in the selected semester.");
        var repeated=repeatedCourses(cumulative);
        var now=totals(current,repeated);var all=totals(cumulative,repeated);
        return new SemesterResult(student,semester,now.registered(),now.earned(),now.quality(),now.gpa(),all.gpa());
    }

    /** Rows, calculations and saved-state validation come from one authorized database transaction. */
    public SemesterView getSemesterView(int student,int semester)throws SQLException {
        return AcademicTransaction.run((c,id)->{
            access.transcript(c,id,student,false);
            return semesterView(c,student,semester,"STUDENT".equals(access.actor(c,id).getRole()));
        });
    }
    private SemesterView semesterView(Connection c,int student,int semester,boolean studentView)throws SQLException {
        var rows=dao.getCourseRowsForSemester(c,student,semester);
        var cumulative=new ArrayList<GpaCourseRow>();
        for(var s:dao.getSemestersForStudent(c,student)) {
            cumulative.addAll(dao.getCourseRowsForSemester(c,student,s.semesterId()));
            if(s.semesterId()==semester)break;
        }
        var repeated=repeatedCourses(cumulative);
        var totals=totals(rows,repeated);var cumulativeTotals=totals(cumulative,repeated);
        var current=calculate(c,student,semester);
        var stored=dao.getByStudentAndSemester(c,student,semester);
        boolean savedCurrent=sameSummary(stored,current);
        String state=stored==null ? "Not saved." : savedCurrent ? "Saved and current." : "Saved GPA is stale; recalculate and save.";
        String detail=cumulativeTotals.repeatPending() ? "Repeat replacement policy is unconfirmed. Affected GPA is Pending; repeated courses are not counted twice."
                : cumulativeTotals.excludedCourses()>0 ? "Provisional: unpublished/incomplete courses are excluded from the published-results calculation."
                : totals.includedCredits()==0 ? "Pending: no included published credits." : "Credit-weighted published results.";
        if(studentView && !savedCurrent) {
            current.setSgpa(null);current.setCgpa(null);
            state="Pending: " + (stored==null ? "GPA has not been saved." : "saved GPA no longer matches current published results.")
                    + " Ask an authorized lecturer to calculate and save it.";
        } else if(savedCurrent) current=stored;
        return new SemesterView(rows,current,savedCurrent,totals.includedCredits(),totals.excludedCourses(),repeated,state+" "+detail);
    }
    private static boolean sameSummary(SemesterResult saved,SemesterResult current) {
        return saved!=null && saved.getCalculatedAt()!=null && same(saved.getSgpa(),current.getSgpa())
                && same(saved.getCgpa(),current.getCgpa()) && same(saved.getTotalRegisteredCredits(),current.getTotalRegisteredCredits())
                && same(saved.getTotalEarnedCredits(),current.getTotalEarnedCredits()) && same(saved.getTotalQualityPoints(),current.getTotalQualityPoints());
    }
    private static boolean same(Double a,Double b) {
        return a==null ? b==null : b!=null && BigDecimal.valueOf(a).setScale(2,RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(b).setScale(2,RoundingMode.HALF_UP))==0;
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
    public SemesterResult getSemesterResult(int student,int semester)throws SQLException{return getSemesterView(student,semester).result();}
    public List<GpaCourseRow> getCourseRows(int student,int semester)throws SQLException {
        return AcademicTransaction.run((c,id)->{access.transcript(c,id,student,false);return dao.getCourseRowsForSemester(c,student,semester);});
    }
    public List<SemesterResult> getSemesterResultsByStudent(int student)throws SQLException {
        return AcademicTransaction.run((c,id)->{
            access.transcript(c,id,student,false);var rows=new ArrayList<SemesterResult>();
            boolean studentView="STUDENT".equals(access.actor(c,id).getRole());
            for(var s:dao.getSemestersForStudent(c,student))rows.add(semesterView(c,student,s.semesterId(),studentView).result());
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
