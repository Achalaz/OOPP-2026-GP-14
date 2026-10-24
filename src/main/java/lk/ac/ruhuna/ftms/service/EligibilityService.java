package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.AssessmentInput;
import lk.ac.ruhuna.ftms.model.*;
import java.math.*;
import java.sql.*;
import java.util.*;

/** CA uses the complete assessment plan. Unknown attendance policy keeps the overall decision pending. */
public class EligibilityService {
    private final EligibilityDAO dao=new EligibilityDAO();
    private final AcademicAccessDAO access=new AcademicAccessDAO();
    private final AttendanceEligibilityService attendancePolicy;

    public EligibilityService() { this(new AttendanceEligibilityService()); }
    /** Only backend wiring with an approved policy supplies this service; UI percentages are never trusted. */
    public EligibilityService(AttendanceEligibilityService attendancePolicy) {
        this.attendancePolicy=Objects.requireNonNull(attendancePolicy);
    }
    public List<EligibilityOffering> getOfferings() throws SQLException {
        return new LecturerCourseAssignmentDAO().findAssigned(new LecturerAccessService().requireLecturer());
    }
    public List<EligibilityRow> getLecturerEligibility(int offering) throws SQLException {
        var lecturer=new LecturerAccessService(); lecturer.requireOffering(offering);
        var rows=dao.findByOfferingId(offering); lecturer.requireOffering(offering); return rows;
    }

    public record CaCalculation(Double weightedScore, Double percentage, Boolean eligible) {}
    /** A missing positive-weight assessment never disappears from the denominator. */
    public static CaCalculation calculateCA(List<AssessmentInput> inputs) {
        BigDecimal weight=BigDecimal.ZERO, score=BigDecimal.ZERO;
        boolean complete=true;
        for(var a:inputs) {
            if(a.maxMark()==null || a.maxMark().signum()<=0 || a.weightPercentage()==null
                || a.weightPercentage().signum()<0 || a.weightPercentage().compareTo(BigDecimal.valueOf(100))>0)
                throw new IllegalArgumentException("Invalid assessment maximum or weight.");
            if("FINAL_EXAM".equals(a.assessmentType()) || a.weightPercentage().signum()==0)continue;
            weight=weight.add(a.weightPercentage());
            if(!"PRESENT".equals(a.markStatus()) || a.marksObtained()==null) {complete=false;continue;}
            if(a.marksObtained().signum()<0 || a.marksObtained().compareTo(a.maxMark())>0)
                throw new IllegalArgumentException("CA mark is outside its assessment maximum.");
            score=score.add(a.marksObtained().multiply(a.weightPercentage()).divide(a.maxMark(),MathContext.DECIMAL128));
        }
        if(!complete || weight.signum()==0)return new CaCalculation(null,null,null);
        if(weight.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("CA weight exceeds 100%.");
        BigDecimal percentage=score.multiply(BigDecimal.valueOf(100)).divide(weight,2,RoundingMode.HALF_UP);
        return new CaCalculation(score.setScale(2,RoundingMode.HALF_UP).doubleValue(),percentage.doubleValue(),
                percentage.compareTo(BigDecimal.valueOf(40))>=0);
    }
    public EligibilityResult evaluate(int enrollment, boolean save) {
        try {
            return AcademicTransaction.run((c,id)->{
                int offering=access.enrollment(c,enrollment);
                access.offering(c,id,offering);
                if(save)access.unpublished(c,enrollment);
                var data=new CourseResultDAO().loadCalculationData(c,enrollment,true);
                if(data==null || data.mismatchedMarks())throw new IllegalArgumentException("Invalid assessment/enrollment relationship.");
                var ca=calculateCA(data.assessments());
                BigDecimal planWeight=data.assessments().stream().map(AssessmentInput::weightPercentage)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
                if(planWeight.compareTo(BigDecimal.valueOf(100))!=0)ca=new CaCalculation(null,null,null);
                var counts=new AttendanceEligibilityDAO().findByEnrollmentId(c,enrollment);
                Double attendance=counts==null || counts.requiresPolicyDecision() ? null : counts.getCombinedPercentage();
                String reason=ca.percentage()==null ? "CA assessment data is incomplete. " : "";
                reason += counts==null || counts.getTotalSessions()==0 ? "No Theory/Practical attendance sessions. "
                    : "Raw PRESENT/session ratios - Theory: "+counts.getTheoryPercentage()+"%; Practical: "+counts.getPracticalPercentage()+"%. ";
                if(counts!=null && counts.requiresPolicyDecision())reason+="Attendance is incomplete or contains unresolved special statuses. ";
                var confirmed=attendancePolicy.findByEnrollmentId(c,enrollment);
                Boolean attendanceEligible=confirmed.map(AttendanceEligibilityProvider.AttendanceEligibility::eligible).orElse(null);
                Boolean overall=ca.eligible()==null || attendanceEligible==null ? null : ca.eligible() && attendanceEligible;
                reason += confirmed.isEmpty()
                        ? "Attendance cutoff/special-status policy or attendance data is unresolved; overall eligibility is pending."
                        : "Attendance evaluated by the configured confirmed policy; overall eligibility requires both CA and attendance.";
                var result=new EligibilityResult(enrollment,ca.percentage(),ca.eligible(),attendance,attendanceEligible,overall,reason);
                if(save) {
                    dao.saveOrUpdateEligibility(c,result,id.userId());
                    access.invalidateDraft(c,enrollment);
                }
                return result;
            });
        } catch(SQLException error){throw new IllegalStateException("Unable to calculate or save eligibility.",error);}
    }
    public Double calculateCAPercentage(int enrollment) {return evaluate(enrollment,false).getCaPercentage();}
    public Boolean isCAEligible(int enrollment) {return evaluate(enrollment,false).isCaEligible();}
    public double calculateCAWeightedScore(int enrollment) {
        try { return AcademicTransaction.run((c,id)->{
            access.offering(c,id,access.enrollment(c,enrollment));
            var result=calculateCA(new CourseResultDAO().loadCalculationData(c,enrollment,true).assessments());
            if(result.weightedScore()==null)throw new IllegalStateException("CA assessment data is incomplete.");
            return result.weightedScore();
        }); }catch(SQLException e){throw new IllegalStateException(e);}
    }
    /** Legacy arguments cannot supply an attendance decision or impersonate the calculator. */
    public EligibilityResult evaluateEligibility(int enrollment,double ignoredPercentage,boolean ignoredDecision) {
        return evaluate(enrollment,false);
    }
    public EligibilityResult evaluateAndSaveEligibility(int enrollment,double ignoredPercentage,boolean ignoredDecision,Integer actor) {
        if(actor!=null && actor!=lk.ac.ruhuna.ftms.util.SessionManager.requireSession().userId())
            throw new SecurityException("Calculated-by must be the signed-in user.");
        return evaluate(enrollment,true);
    }
}
