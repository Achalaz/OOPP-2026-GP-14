package lk.ac.ruhuna.ftms.service;
import java.sql.SQLException;
import java.util.*;
import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.model.*;
public class LecturerViewService {
    private final LecturerAccessService access=new LecturerAccessService();
    public List<EligibilityOffering> offerings() throws SQLException {
        var result=new LecturerCourseAssignmentDAO().findAssigned(access.requireLecturer());
        access.requireLecturer();return result;
    }
    public LecturerTable load(String view,int offering) throws SQLException {
        access.requireOffering(offering);
        var dao=new LecturerViewDAO();
        LecturerTable result=switch(view) {
            case "students" -> dao.students(offering);
            case "marks" -> dao.marks(offering);
            case "medical" -> new MedicalService().getLecturerStatuses(offering);
            case "gpa" -> new GPAService().getLecturerGpa(offering);
            case "eligibility" -> eligibility(offering);
            case "attendance" -> attendance(offering);
            case "results" -> results(offering);
            default -> throw new IllegalArgumentException("Unknown lecturer view.");
        };
        access.requireOffering(offering);return result;
    }
    private LecturerTable eligibility(int offering) throws SQLException {
        var rows=new ArrayList<List<String>>();
        for(var r:new EligibilityService().getLecturerEligibility(offering))
            rows.add(row(r.enrollmentId(),r.caPercentage(),r.attendancePercentage(),r.overallEligible(),r.saved()?r.reason():"Not evaluated"));
        return new LecturerTable(List.of("Enrollment","CA %","Attendance %","Eligible","Decision"),rows);
    }
    private LecturerTable attendance(int offering) throws SQLException {
        var rows=new ArrayList<List<String>>();
        for(var r:new AttendanceService().getSummariesByOffering(offering))
            rows.add(row(r.getRegistrationNo(),r.getStudentName(),r.getTotalSessions(),r.getTotalPresent(),r.getCombinedPercentage(),r.getUnmarkedCount()));
        return new LecturerTable(List.of("Registration","Name","Sessions","Present","Attendance %","Unmarked"),rows);
    }
    private LecturerTable results(int offering) throws SQLException {
        var rows=new ArrayList<List<String>>();
        // Reports supplies labels/marks; ResultService supplies the authoritative publication flag.
        for(var r:new ReportDAO().getOfferingResultReport(offering)) {
            var saved=new ResultService().getResultByEnrollmentId(r.getEnrollmentId());
            if(saved!=null && saved.isPublished()) rows.add(row(r.getRegistrationNo(),r.getStudentName(),
                saved.getCaMark(),saved.getFinalExamMark(),saved.getOverallMark(),saved.getGradeLetter(),saved.getGradePoint(),saved.getResultStatus()));
        }
        return new LecturerTable(List.of("Registration","Name","CA","Exam","Overall","Grade","Points","Status"),rows);
    }
    private static List<String> row(Object... values) {
        return Arrays.stream(values).map(v->Objects.toString(v,"—")).toList();
    }
    public void saveMark(int offering,Mark mark) throws SQLException {
        access.requireOffering(offering);
        int actual=new LecturerCourseAssignmentDAO().enrollmentOffering(mark.getEnrollmentId(),mark.getAssessmentId(),mark.getAttemptNo());
        if(actual!=offering) throw new SecurityException("Select an assessment in this offering.");
        new MarkService().saveMark(mark);
    }
}
