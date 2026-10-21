package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Manages plans in assigned offerings; a complete result requires exactly 100% weight. */
public class AssessmentService {
    public static final List<String> TYPES=List.of("QUIZ","ASSIGNMENT","MID","PRACTICAL","PROJECT","PRESENTATION","FINAL_EXAM","OTHER");
    private final AssessmentDAO dao=new AssessmentDAO();
    private final AcademicAccessDAO access=new AcademicAccessDAO();

    public List<EligibilityOffering> getOfferings() throws SQLException {
        return new LecturerCourseAssignmentDAO().findAssigned(new LecturerAccessService().requireLecturer());
    }
    public List<Assessment> getAssessments(int offering) throws SQLException {
        return AcademicTransaction.run((c,id)->{access.offering(c,id,offering);return dao.findByOffering(c,offering);});
    }
    public int save(Assessment assessment) throws SQLException {
        validate(assessment);
        return AcademicTransaction.run((c,id)->{
            access.offering(c,id,assessment.getOfferingId());
            var plan=dao.findByOffering(c,assessment.getOfferingId());
            Assessment old=plan.stream().filter(a->a.getAssessmentId()==assessment.getAssessmentId()).findFirst().orElse(null);
            if(assessment.getAssessmentId()!=0 && old==null)throw new SecurityException("Assessment does not belong to this offering.");
            if(plan.stream().anyMatch(a->a.getAssessmentId()!=assessment.getAssessmentId()
                && a.getAssessmentName().equalsIgnoreCase(assessment.getAssessmentName().trim())))
                throw new IllegalArgumentException("An assessment with this name already exists in the offering.");
            BigDecimal weight=BigDecimal.valueOf(assessment.getWeightPercentage());
            for(var a:plan) if(a.getAssessmentId()!=assessment.getAssessmentId()) weight=weight.add(BigDecimal.valueOf(a.getWeightPercentage()));
            if(weight.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Assessment weights cannot exceed 100%.");
            if(old!=null && dao.hasMarks(c,old.getAssessmentId()) &&
               (old.getMaxMark()!=assessment.getMaxMark() || old.getWeightPercentage()!=assessment.getWeightPercentage()
                 || !old.getAssessmentType().equals(assessment.getAssessmentType())))
                throw new IllegalArgumentException("Type, maximum and weight are protected once marks exist.");
            var enrollments=editableEnrollments(c,assessment.getOfferingId());
            int saved=dao.save(c,assessment,id.userId());
            for(int enrollment:enrollments)access.invalidate(c,enrollment);
            return saved;
        });
    }
    public void delete(int offering,int assessment) throws SQLException {
        AcademicTransaction.run((c,id)->{
            access.offering(c,id,offering);
            if(dao.findByOffering(c,offering).stream().noneMatch(a->a.getAssessmentId()==assessment))
                throw new SecurityException("Assessment does not belong to this offering.");
            if(dao.hasMarks(c,assessment))throw new IllegalArgumentException("An assessment with marks cannot be deleted.");
            var enrollments=editableEnrollments(c,offering);
            dao.delete(c,offering,assessment);
            for(int enrollment:enrollments)access.invalidate(c,enrollment);
            return null;
        });
    }
    private List<Integer> editableEnrollments(Connection c,int offering)throws SQLException {
        var ids=access.offeringEnrollments(c,offering);
        for(int id:ids)access.unpublished(c,id);
        return ids;
    }
    public static void validate(Assessment a) {
        if(a==null || a.getOfferingId()<=0 || a.getAssessmentId()<0)throw new IllegalArgumentException("Select a valid offering.");
        if(a.getAssessmentName()==null || a.getAssessmentName().isBlank() || a.getAssessmentName().trim().length()>120)
            throw new IllegalArgumentException("Assessment name must contain 1 to 120 characters.");
        if(a.getAssessmentType()==null || !TYPES.contains(a.getAssessmentType()))throw new IllegalArgumentException("Select a valid assessment type.");
        decimal(a.getMaxMark(),0.01,99999.99,"Maximum mark");
        decimal(a.getWeightPercentage(),0,100,"Weight");
    }
    private static void decimal(double value,double min,double max,String label) {
        if(!Double.isFinite(value) || value<min || value>max || BigDecimal.valueOf(value).stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException(label+" must be between "+min+" and "+max+" with at most two decimal places.");
    }
}
