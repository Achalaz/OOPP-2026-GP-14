package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.*;
import java.sql.*;

/** Run explicitly: mvn -Dtest=AcademicMySqlIT test. All writes target temporary MySQL tables. */
public class AcademicMySqlIT extends TestCase {
    private AcademicDatabaseFixture db;
    protected void setUp()throws Exception{db=new AcademicDatabaseFixture();db.login(1,"LECTURER");}
    protected void tearDown()throws Exception{db.close();}
    private void completeMarks(){
        var marks=new MarkService();marks.saveMark(new Mark(100,10,40.0,1));marks.saveMark(new Mark(100,11,80.0,1));
    }
    public void testAssessmentCrudAndReferenceProtection()throws Exception{
        var service=new AssessmentService();
        var a=new Assessment(20,"Practice","PRACTICAL",25,0,null,"Fixture");
        int id=service.save(a);assertTrue(id>0);assertEquals(2,service.getAssessments(20).size());
        a.setAssessmentId(id);a.setAssessmentName("Updated");service.save(a);
        assertTrue(service.getAssessments(20).stream().anyMatch(v->v.getAssessmentName().equals("Updated")));
        service.delete(20,id);assertEquals(1,service.getAssessments(20).size());
        reject(()->service.save(new Assessment(20,"Too much","QUIZ",100,1,null,null)));
        completeMarks();reject(()->service.delete(10,10));
        var referenced=service.getAssessments(10).getFirst();referenced.setMaxMark(40);reject(()->service.save(referenced));
        db.login(2,"LECTURER");reject(()->service.getAssessments(10));reject(()->service.save(new Assessment(10,"No access","QUIZ",10,0,null,null)));
    }
    public void testMarksPersistenceStatesAndAuthorization()throws Exception{
        var service=new MarkService();
        service.saveMark(new Mark(100,10,0.0,1));
        assertEquals(1,service.getAllMarks().size());assertEquals(0.0,service.getAllMarks().getFirst().getMarksObtained(),0.00001);
        var absent=new Mark(100,10,null,1);absent.setMarkStatus("ABSENT");service.saveMark(absent);
        assertEquals("ABSENT",service.getAllMarks().getFirst().getMarkStatus());assertNull(service.getAllMarks().getFirst().getMarksObtained());
        for(double mark:new double[]{-1,51,Double.NaN,Double.POSITIVE_INFINITY})reject(()->service.saveMark(new Mark(100,10,mark,1)));
        reject(()->service.saveMark(new Mark(100,20,5.0,1)));
        for(var actor:List.of(new Object[]{2,"LECTURER"},new Object[]{3,"STUDENT"},new Object[]{5,"ADMIN"},new Object[]{6,"TECHNICAL_OFFICER"})){
            db.login((Integer)actor[0],(String)actor[1]);reject(()->new MarkService().saveMark(new Mark(100,10,5.0,1)));
        }
        SessionManager.logout();reject(()->new MarkService().saveMark(new Mark(100,10,5.0,1)));
    }
    public void testEligibilityPendingAndAttendance()throws Exception{
        var service=new EligibilityService();var before=service.evaluate(100,true);
        assertNull(before.getCaPercentage());assertNull(before.isOverallEligible());
        completeMarks();var result=service.evaluate(100,true);
        assertEquals(80.0,result.getCaPercentage(),0.00001);assertEquals(Boolean.TRUE,result.isCaEligible());
        assertEquals(100.0,result.getAttendancePercentage(),0.00001);assertNull(result.isAttendanceEligible());assertNull(result.isOverallEligible());
        assertNull(new EligibilityDAO().getByEnrollmentId(100).isOverallEligible());
        assertEquals(0.0,service.evaluate(101,false).getAttendancePercentage(),0.00001);
        db.execute("UPDATE attendance_records SET attendance_status='MEDICAL' WHERE enrollment_id=101 AND session_id=1");
        assertNull(service.evaluate(101,false).getAttendancePercentage());
        db.execute("INSERT INTO enrollments(enrollment_id,offering_id,student_id) VALUES(201,20,4)");
        assertNull(service.evaluate(201,false).getAttendancePercentage());
        reject(()->service.evaluate(300,false));
    }
    public void testDraftPublishProtectsInputsAndUpdatesReports()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        var service=new ResultService();var draft=service.calculateAndSaveResult(100);
        assertFalse(draft.isPublished());assertEquals(Integer.valueOf(1),draft.getCalculatedBy());
        assertNull(new ReportService().getStudentReport(3).getFirst().getOverallMark());
        var published=service.publishResult(10,draft.publicationSnapshot());
        assertTrue(published.isPublished());assertNotNull(published.getPublishedAt());
        assertEquals(draft.getCalculatedAt(),published.getCalculatedAt());
        reject(()->service.publishResult(10,draft.publicationSnapshot()));
        reject(()->service.calculateAndSaveResult(100));
        reject(()->new MarkService().saveMark(new Mark(100,10,30.0,1)));
        reject(()->new AssessmentService().save(new Assessment(10,"Extra","QUIZ",10,0,null,null)));
        assertTrue(new CourseResultDAO().getByEnrollmentId(100).isPublished());
        assertEquals(80.0,new ReportService().getStudentReport(3).getFirst().getOverallMark(),0.00001);
        assertEquals(2,new ReportService().getMarkDetails(100).size());
        assertEquals(1,new ReportService().countPassed(new ReportService().getOfferingReport(10)));
    }
    public void testRollbackRestoresMarkAndEligibility()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        var saved=new ResultService().calculateAndSaveResult(100);
        db.failAfterWrite("INSERT INTO marks");
        reject(()->new MarkService().saveMark(new Mark(100,10,10.0,1)));
        assertEquals(40.0,new MarkService().getAllMarks().getFirst().getMarksObtained(),0.00001);
        assertEquals(saved.publicationSnapshot(),new CourseResultDAO().getByEnrollmentId(100).publicationSnapshot());
        db.afterWrite(SessionManager::logout);
        reject(()->new MarkService().saveMark(new Mark(100,10,20.0,1)));
        db.login(1,"LECTURER");assertEquals(40.0,new MarkService().getAllMarks().getFirst().getMarksObtained(),0.00001);
    }
    public void testStaleDraftAndPublishRollback()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        var service=new ResultService();var draft=service.calculateAndSaveResult(100);
        db.execute("UPDATE marks SET marks_obtained=70 WHERE enrollment_id=100 AND assessment_id=11");
        var stale=draft.publicationSnapshot();reject(()->service.publishResult(10,stale));
        db.execute("UPDATE marks SET marks_obtained=80 WHERE enrollment_id=100 AND assessment_id=11");db.confirmedFixtureEligibility(100);
        draft=service.calculateAndSaveResult(100);var reviewed=draft.publicationSnapshot();
        db.failAfterWrite("SET published = TRUE");reject(()->service.publishResult(10,reviewed));
        assertFalse(new CourseResultDAO().getByEnrollmentId(100).isPublished());
        db.login(2,"LECTURER");reject(()->service.publishResult(10,reviewed));
        db.login(5,"ADMIN");reject(()->service.publishResult(10,reviewed));
    }
    public void testGpaChronologyPersistenceAndStudentPrivacy()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        var resultService=new ResultService();var first=resultService.calculateAndSaveResult(100);resultService.publishResult(10,first.publicationSnapshot());
        new MarkService().saveMark(new Mark(200,20,60.0,1));db.confirmedFixtureEligibility(200);
        var second=resultService.calculateAndSaveResult(200);resultService.publishResult(20,second.publicationSnapshot());
        var gpa=new GPAService();
        assertEquals(9,gpa.getSemestersForStudent(3).getFirst().semesterId());
        // Earlier semester: 3*4/3=4; later semester: 2*3/2=3; cumulative 18/5=3.60.
        var result=gpa.calculateSemesterGPA(3,1);assertEquals(3.0,result.getSgpa(),0.00001);assertEquals(3.6,result.getCgpa(),0.00001);
        result.setSgpa(99.0);gpa.saveSemesterResult(result);
        assertEquals(3.0,new SemesterResultDAO().getByStudentAndSemester(3,1).getSgpa(),0.00001);
        db.login(3,"STUDENT");assertEquals(3.6,new GPAService().calculateCGPA(3,1),0.00001);
        reject(()->new GPAService().saveSemesterResult(result));reject(()->new GPAService().calculateCGPA(4,1));
        assertEquals(1,new ReportService().getStudents().size());reject(()->new ReportService().getStudentReport(4));
        reject(()->new ReportService().getOfferingReport(10));reject(()->new ReportService().getBatchReport(2024));
        db.login(1,"LECTURER");assertEquals(2,new ReportService().getBatchReport(2024).size());
        assertEquals(1,new ReportService().getStudentReport(4).size()); // offering 30 hidden
        reject(()->new GPAService().calculateCGPA(4,1)); // full transcript contains an unassigned offering
    }
    private void reject(Action action)throws Exception{try{action.run();fail("Expected operation rejection");}catch(RuntimeException|SQLException expected){}}
    public void testAssessmentRollbackAndCancelledOffering()throws Exception{
        var service=new AssessmentService();
        db.failAfterWrite("INSERT INTO assessments");
        reject(()->service.save(new Assessment(20,"Rollback","QUIZ",20,0,null,null)));
        assertEquals(1,service.getAssessments(20).size());
        db.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=20");
        assertFalse(new ResultService().canPublishResult(20));
        reject(()->service.save(new Assessment(20,"Cancelled","QUIZ",20,0,null,null)));
        reject(()->new MarkService().saveMark(new Mark(200,20,10.0,1)));
    }
    public void testMarkEditInvalidatesDraftAndCaWithoutUnpublishing()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        new ResultService().calculateAndSaveResult(100);
        new MarkService().saveMark(new Mark(100,10,35.0,1));
        var eligibility=new EligibilityDAO().getByEnrollmentId(100);
        assertNull(eligibility.getCaPercentage());assertNull(eligibility.isOverallEligible());
        var result=new CourseResultDAO().getByEnrollmentId(100);
        assertNull(result.getGradePoint());assertEquals(CourseResult.ResultStatus.PENDING,result.getResultStatus());
    }
    public void testHistoricalGradeSnapshotSurvivesRuleEdits()throws Exception{
        completeMarks();db.confirmedFixtureEligibility(100);
        var service=new ResultService();var draft=service.calculateAndSaveResult(100);service.publishResult(10,draft.publicationSnapshot());
        db.execute("UPDATE grade_rules SET grade_point=0,is_pass=FALSE WHERE grade_rule_id=3");
        var gpa=new GPAService().calculateSemesterGPA(3,9);
        assertEquals(4.0,gpa.getSgpa(),0.00001);assertEquals(3.0,gpa.getTotalEarnedCredits(),0.00001);
    }
    public void testIncompleteInactiveAndReplacedSessionCannotPublish()throws Exception{
        var service=new ResultService();
        var incomplete=service.calculateAndSaveResult(100);
        reject(()->service.publishResult(10,incomplete.publicationSnapshot()));
        assertFalse(new CourseResultDAO().getByEnrollmentId(100).isPublished());
        completeMarks();db.confirmedFixtureEligibility(100);
        var draft=service.calculateAndSaveResult(100);
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=1");
        assertFalse(service.canPublishResult(10));
        reject(()->service.publishResult(10,draft.publicationSnapshot()));
        db.execute("UPDATE users SET is_active=TRUE WHERE user_id=1");
        db.afterWrite(()->db.login(1,"LECTURER"));
        reject(()->service.publishResult(10,draft.publicationSnapshot()));
        assertEquals(draft.publicationSnapshot(),new CourseResultDAO().getByEnrollmentId(100).publicationSnapshot());
    }
    private interface Action{void run()throws Exception;}
}
