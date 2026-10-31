package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.AcademicAccessDAO.OfferingGap;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO;
import lk.ac.ruhuna.ftms.model.SemesterResult;
import java.util.List;
import static lk.ac.ruhuna.ftms.service.StudentAccessTest.denied;

/** Actual MySQL statements; every mutation is confined to existing connection-local fixtures. */
public class GpaSelectionMySqlIT extends TestCase {
    private StudentDatabaseFixture db;
    private final GPAService gpa=new GPAService();
    protected void setUp() throws Exception { db=new StudentDatabaseFixture();db.academic.login(1,"LECTURER"); }
    protected void tearDown() throws Exception { db.close(); }

    public void testFullAccessSelectionCalculationSaveAndRefresh() throws Exception {
        db.publishSamples();
        var selection=gpa.getStudentSelection();
        assertEquals(List.of(3),selection.students().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        assertEquals(2,selection.assignedOfferings()); // duplicate PRIMARY/ASSISTANT is one offering
        assertEquals(1,selection.blockedStudents());
        assertEquals(List.of(30),selection.missingAssignments().stream().map(OfferingGap::offeringId).toList());
        assertFalse(selection.toString().contains("TEST/4"));
        assertEquals(selection.students(),gpa.getStudents());
        assertEquals(List.of(9,1),gpa.getSemestersForStudent(3).stream().map(SemesterResultDAO.SemesterOption::semesterId).toList());
        var view=gpa.getSemesterView(3,9);
        assertEquals(4.0,view.result().getSgpa(),0.00001);
        assertEquals(4.0,view.result().getCgpa(),0.00001);
        assertFalse(view.savedCurrent());
        gpa.saveSemesterResult(view.result());
        var refreshed=new GPAService().getSemesterView(3,9);
        assertTrue(refreshed.savedCurrent());
        assertEquals(4.0,refreshed.result().getSgpa(),0.00001);
        assertEquals(2,new ReportService().getOfferingReport(10).size());
        denied(()->new ReportService().getOfferingReport(30));
    }

    public void testPartialAccessExplainsMetadataWithoutDisclosingTranscripts() throws Exception {
        db.publishSamples();db.academic.login(2,"LECTURER");
        var selection=gpa.getStudentSelection();
        assertTrue(selection.students().isEmpty());assertEquals(1,selection.blockedStudents());
        assertEquals(List.of(10),selection.missingAssignments().stream().map(OfferingGap::offeringId).toList());
        assertTrue(selection.message().contains("unassigned offerings"));
        assertFalse(selection.toString().contains("TEST/3"));assertFalse(selection.toString().contains("TEST/4"));
        denied(()->gpa.getSemesterView(4,9));denied(()->gpa.calculateSemesterGPA(4,1));
        denied(()->gpa.getCourseRows(4,9));denied(()->gpa.getSemestersForStudent(4));
        denied(()->gpa.saveSemesterResult(new SemesterResult(4,1,1,1,4,4.0,4.0)));
        var reports=new ReportService();
        assertEquals(List.of(30),reports.getCourseOfferings().stream().map(r->r.offeringId()).toList());
        assertEquals(List.of("TEST-C"),reports.getStudentReport(4).stream().map(r->r.getCourseCode()).toList());
        assertNull(reports.getOfferingReport(30).getFirst().getGradeLetter());
        denied(()->reports.getOfferingReport(10));
    }

    public void testNoAssignmentsAndNoEnrollmentsAreDifferent() throws Exception {
        db.academic.login(2,"LECTURER");
        db.academic.execute("DELETE FROM enrollments WHERE offering_id=30");
        var empty=gpa.getStudentSelection();
        assertEquals(1,empty.assignedOfferings());assertEquals(0,empty.blockedStudents());
        assertTrue(empty.message().contains("no available student enrollments"));
        db.academic.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=2");
        var none=gpa.getStudentSelection();
        assertEquals(0,none.assignedOfferings());assertTrue(none.missingAssignments().isEmpty());
        assertTrue(none.students().isEmpty());assertTrue(none.message().startsWith("No lecturer course assignments"));
    }

    public void testMissingProfilesAndInactiveRolesAreNotSilentlyExcluded() throws Exception {
        db.academic.execute("DELETE FROM lecturers WHERE lecturer_id=1");
        try { gpa.getStudentSelection();fail("Missing profile accepted"); }
        catch(SecurityException error){assertTrue(error.getMessage().contains("lecturer profile is missing"));}
        db.academic.login(3,"STUDENT");db.academic.execute("DELETE FROM students WHERE student_id=3");
        denied(gpa::getStudentSelection);
        db.academic.login(2,"LECTURER");db.academic.execute("UPDATE users SET is_active=FALSE WHERE user_id=2");
        denied(gpa::getStudentSelection);
        db.academic.login(5,"ADMIN");denied(gpa::getStudentSelection);
    }

    public void testStudentOwnPublishedResultsAndNoPublishedResults() throws Exception {
        db.academic.login(3,"STUDENT");
        assertEquals(List.of(3),gpa.getStudents().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        assertTrue(gpa.getStudentSelection().missingAssignments().isEmpty());
        assertNull(gpa.getSemesterView(3,9).result().getSgpa());
        db.publishSamples();db.academic.login(3,"STUDENT");
        assertEquals(4.0,gpa.getSemesterView(3,9).result().getSgpa(),0.00001);
        assertTrue(gpa.getSemesterView(3,1).rows().stream().noneMatch(SemesterResultDAO.GpaCourseRow::published));
        assertNull(gpa.getSemesterView(3,1).rows().getFirst().gradePoint());
        denied(()->gpa.getSemesterView(4,9));
        denied(()->gpa.saveSemesterResult(gpa.getSemesterView(3,9).result()));
    }

    public void testAssignmentChangesRefreshWithoutGrantingGpaOnPartialScope() throws Exception {
        db.academic.login(2,"LECTURER");assertTrue(gpa.getStudents().isEmpty());
        db.academic.execute("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(10,2,'ASSISTANT')");
        assertEquals(List.of(4),gpa.getStudents().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        assertEquals(List.of(20),gpa.getStudentSelection().missingAssignments().stream().map(OfferingGap::offeringId).toList());
        db.academic.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=2 AND offering_id=10");
        assertTrue(gpa.getStudents().isEmpty());denied(()->gpa.calculateSemesterGPA(4,1));
    }

    public void testHistoricalOfferingsRequireAssignmentsButWithdrawnAndCancelledDoNot() throws Exception {
        db.academic.login(2,"LECTURER");
        db.academic.execute("UPDATE course_offerings SET status='COMPLETED' WHERE offering_id=10");
        assertEquals(List.of(10),gpa.getStudentSelection().missingAssignments().stream().map(OfferingGap::offeringId).toList());
        db.academic.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE enrollment_id=101");
        assertEquals(List.of(4),gpa.getStudents().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        db.academic.execute("UPDATE enrollments SET status='ENROLLED' WHERE enrollment_id=101");
        db.academic.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=10");
        assertEquals(List.of(4),gpa.getStudents().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
    }

    public void testRepeatPolicyStaysPendingWithFullAuthorization() throws Exception {
        db.publishSamples();db.academic.execute("UPDATE enrollments SET attempt_no=2 WHERE enrollment_id=100");
        var view=gpa.getSemesterView(3,9);
        assertNull(view.result().getSgpa());assertNull(view.result().getCgpa());
        assertTrue(view.message().contains("Repeat replacement policy is unconfirmed"));
        assertEquals("A",view.rows().getFirst().gradeLetter());
    }
}
