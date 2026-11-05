package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.AssessmentInput;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.GpaCourseRow;
import lk.ac.ruhuna.ftms.dao.AttendanceEligibilityDAO.AttendanceCounts;
import lk.ac.ruhuna.ftms.model.*;
import java.math.BigDecimal;
import java.util.*;

public class AcademicCalculationsTest extends TestCase {
    private AssessmentInput ca(String mark,String status,String max,String weight){
        return new AssessmentInput(1,"QUIZ",new BigDecimal(max),new BigDecimal(weight),mark==null?null:new BigDecimal(mark),status);
    }
    private GpaCourseRow row(String course,double credits,Double point,String status,boolean published){
        return new GpaCourseRow(1,1,course,course,credits,"ENROLLED","X",point,status,"PASS".equals(status),published);
    }
    public void testCaBoundary(){
        for(String mark:List.of("39.99","40","40.01")){
            var result=EligibilityService.calculateCA(List.of(ca(mark,"PRESENT","100","40")));
            assertEquals(Double.valueOf(mark),result.percentage());
            assertEquals(Double.parseDouble(mark)>=40,result.eligible().booleanValue());
        }
    }
    public void testMissingCaNeverInflatesPercentage(){
        assertNull(EligibilityService.calculateCA(List.of(ca("20","PRESENT","20","20"),ca(null,"PENDING","20","20"))).percentage());
        assertNull(EligibilityService.calculateCA(List.of(ca(null,"ABSENT","20","20"))).eligible());
        assertNull(EligibilityService.calculateCA(List.of()).percentage());
        assertEquals(0.0,EligibilityService.calculateCA(List.of(ca("0","PRESENT","20","20"))).percentage(),0.00001);
    }
    public void testWeightedCa(){
        // 10/20*10 + 45/50*30 = 32/40 => 80%.
        var result=EligibilityService.calculateCA(List.of(ca("10","PRESENT","20","10"),ca("45","PRESENT","50","30")));
        assertEquals(32.0,result.weightedScore(),0.00001);assertEquals(80.0,result.percentage(),0.00001);
    }
    public void testSgpaUsesCreditsAndIncludesZeroFail(){
        // (3*4 + 2*3 + 1*0) / 6 = 3.00, earned credits = 5.
        var total=GPAService.totals(List.of(row("A",3,4.0,"PASS",true),row("B",2,3.0,"PASS",true),row("C",1,0.0,"FAIL",true)));
        assertEquals(3.0,total.gpa(),0.00001);assertEquals(5.0,total.earned(),0.00001);assertEquals(18.0,total.quality(),0.00001);
    }
    public void testCumulativeWeightingNotAverageOfSgpas(){
        // Semester 1: 2 credits at 4.0; semester 2: 8 credits at 2.0 => 24/10 = 2.40.
        var total=GPAService.totals(List.of(row("A",2,4.0,"PASS",true),row("B",8,2.0,"PASS",true)));
        assertEquals(2.4,total.gpa(),0.00001);
    }
    public void testUnpublishedAndIncompleteExcluded(){
        var total=GPAService.totals(List.of(row("A",3,4.0,"PASS",false),row("B",2,null,"PENDING",true),row("C",1,2.0,"PASS",true)));
        assertEquals(2.0,total.gpa(),0.00001);assertEquals(6.0,total.registered(),0.00001);
        assertNull(GPAService.totals(List.of(row("A",3,null,"PENDING",false))).gpa());
    }
    public void testRepeatPolicyRemainsPending(){
        var total=GPAService.totals(List.of(row("A",3,4.0,"PASS",true),row("A",3,2.0,"PASS",true),row("B",2,3.0,"PASS",true)));
        assertNull(total.gpa());
        assertTrue(total.repeatPending());
        assertEquals(5.0,total.registered(),0.00001);
        assertEquals(2.0,total.earned(),0.00001);
        assertEquals(6.0,total.quality(),0.00001);
        assertEquals(2.0,total.includedCredits(),0.00001);
        assertEquals(2,total.excludedCourses());
    }
    public void testGpaDoesNotTreatIncompleteOrMissingGradesAsZero(){
        var total=GPAService.totals(List.of(row("A",3,4.0,"PASS",true),
                row("B",8,4.0,"INCOMPLETE",true),row("C",2,null,"PASS",true),
                row("D",1,0.0,"FAIL",false)));
        assertEquals(4.0,total.gpa(),0.00001);
        assertEquals(3.0,total.includedCredits(),0.00001);
        assertEquals(12.0,total.quality(),0.00001);
        assertEquals(3,total.excludedCourses());
        assertNull(GPAService.qualityPoints(row("B",8,4.0,"INCOMPLETE",true)));
        assertNull(GPAService.qualityPoints(row("C",2,null,"PASS",true)));
    }
    public void testZeroCreditPublishedResultKeepsGpaPending(){
        var total=GPAService.totals(List.of(row("A",0,4.0,"PASS",true)));
        assertNull(total.gpa());
        assertEquals(0.0,total.quality(),0.00001);
    }
    public void testGpaUsesConsistentHalfUpRounding(){
        var total=GPAService.totals(List.of(row("A",1,3.125,"PASS",true)));
        assertEquals(3.13,total.gpa(),0.00001);
        assertEquals(3.13,total.quality(),0.00001);
    }
    public void testAttendanceMissingVersusZero(){
        var missing=new AttendanceCounts(1,0,0,0,0,0,0,0,0,0,0);
        var zero=new AttendanceCounts(1,2,1,0,0,3,0,0,0,0,0);
        assertNull(missing.getCombinedPercentage());assertEquals(0.0,zero.getCombinedPercentage(),0.00001);
        var mixed=new AttendanceCounts(1,2,2,2,1,1,0,0,0,0,0);
        assertEquals(100.0,mixed.getTheoryPercentage(),0.00001);assertEquals(50.0,mixed.getPracticalPercentage(),0.00001);
        assertEquals(75.0,mixed.getCombinedPercentage(),0.00001);
    }
    public void testAssessmentAndMarkValidation(){
        var a=new Assessment(1,"Quiz","QUIZ",100,40,null,null);AssessmentService.validate(a);
        for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,100.001}){
            a.setWeightPercentage(invalid);reject(()->AssessmentService.validate(a));
        }
        Mark zero=new Mark(1,1,0.0,1);MarkService.validateStatus(zero);
        Mark pending=new Mark(1,1,null,1);MarkService.validateStatus(pending);
        pending.setMarkStatus("ABSENT");MarkService.validateStatus(pending);
        pending.setMarksObtained(0.0);reject(()->MarkService.validateStatus(pending));
    }
    public void testReportSummaryUsesOnlyNumericPublishedResults(){
        var service=new ReportService();
        var rows=List.of(report("PASS",80.0),report("FAIL",0.0),report("PENDING",null),report("INCOMPLETE",null));
        assertEquals(1,service.countPassed(rows));assertEquals(1,service.countFailed(rows));assertEquals(2,service.countPending(rows));
        assertEquals(40.0,service.calculateAverageOverallMark(rows),0.00001);assertNull(service.calculateAverageOverallMark(List.of()));
    }
    private ResultReportRow report(String status,Double mark){return new ResultReportRow(1,"TEST","Student","C","Course",3,null,null,mark,null,null,status);}
    private void reject(Runnable action){try{action.run();fail("Expected invalid input rejection");}catch(RuntimeException expected){}}
}
