package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseResultDAO;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.AssessmentInput;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.CalculationData;
import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.dao.GradeRuleDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.exception.ResultCalculationException;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.CourseResult.ResultStatus;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.GradeRule;
import lk.ac.ruhuna.ftms.model.ResultRow;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public class ResultService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final CourseResultDAO resultDAO;
    private final GradeRuleDAO gradeRuleDAO;
    private final EligibilityDAO eligibilityDAO;

    public ResultService() {
        this(new CourseResultDAO(), new GradeRuleDAO(), new EligibilityDAO());
    }

    public ResultService(CourseResultDAO resultDAO, GradeRuleDAO gradeRuleDAO, EligibilityDAO eligibilityDAO) {
        this.resultDAO = Objects.requireNonNull(resultDAO);
        this.gradeRuleDAO = Objects.requireNonNull(gradeRuleDAO);
        this.eligibilityDAO = Objects.requireNonNull(eligibilityDAO);
    }

    public List<EligibilityOffering> getOfferings() {
        try { return eligibilityDAO.findOfferings(); }
        catch (SQLException error) { throw systemError("load course offerings", error); }
    }

    public List<ResultRow> getBatchResults(int offeringId) {
        LecturerAccessService.rejectUnscopedLecturerOperation();
        requirePositive(offeringId, "Course offering");
        try { return resultDAO.findEnrollmentRowsByOfferingId(offeringId); }
        catch (SQLException error) { throw systemError("load batch results", error); }
    }

    public CourseResult getResultByEnrollmentId(int enrollmentId) {
        requirePositive(enrollmentId, "Enrollment");
        try {
            var user=lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser();
            boolean lecturer=user!=null && "LECTURER".equals(user.getRole());
            LecturerAccessService access=lecturer ? new LecturerAccessService() : null;
            int offering=lecturer ? new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO().enrollmentOffering(enrollmentId) : 0;
            if(lecturer) access.requireOffering(offering);
            var result=resultDAO.getByEnrollmentId(enrollmentId);
            if(lecturer) access.requireOffering(offering);
            return lecturer && result!=null && !result.isPublished() ? null : result;
        }
        catch (SQLException error) { throw systemError("load the saved result", error); }
    }

    public CourseResult calculateResult(int enrollmentId) {
        return calculateInTransaction(enrollmentId, null, false);
    }

    /** Recalculates from current database inputs; never trusts a UI preview or a supplied grade. */
    public CourseResult calculateAndSaveResult(int enrollmentId, Integer calculatedBy) {
        LecturerAccessService.rejectUnscopedLecturerOperation();
        if (calculatedBy != null) { requirePositive(calculatedBy, "Calculating user"); }
        return calculateInTransaction(enrollmentId, calculatedBy, true);
    }

    private CourseResult calculateInTransaction(int enrollmentId, Integer calculatedBy, boolean save) {
        requirePositive(enrollmentId, "Enrollment");
        try (Connection connection = DBConnection.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                CalculationData data = resultDAO.loadCalculationData(connection, enrollmentId, save);
                if (data == null) {
                    throw new ResultCalculationException("Enrollment " + enrollmentId + " does not exist.");
                }
                CourseResult result = calculate(data, connection);
                result.setCalculatedBy(calculatedBy);
                // Recalculated results are drafts. A separate publication workflow must approve them.
                result.setPublished(false);
                result.setPublishedAt(null);
                if (save) { result = resultDAO.saveOrUpdate(connection, result); }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        } catch (SQLException error) {
            throw systemError(save ? "save the result" : "calculate the result", error);
        }
    }

    /** Package-visible calculation seam for deterministic tests without a live database. */
    CourseResult calculate(CalculationData data, Connection connection) throws SQLException {
        if ("WITHDRAWN".equals(data.enrollmentStatus())) {
            throw new ResultCalculationException("A withdrawn enrollment cannot receive a calculated result.");
        }
        CourseResult result = new CourseResult();
        result.setEnrollmentId(data.enrollmentId());
        if (Boolean.FALSE.equals(data.overallEligible())) {
            result.setResultStatus(ResultStatus.NOT_ELIGIBLE);
            return result;
        }
        if (data.overallEligible() == null) {
            result.setResultStatus(ResultStatus.PENDING);
            return result;
        }
        if (data.mismatchedMarks()) {
            throw new ResultCalculationException("This enrollment has marks for another course offering. Correct them first.");
        }
        if (data.assessments().isEmpty()) {
            result.setResultStatus(ResultStatus.INCOMPLETE);
            return result;
        }

        BigDecimal caWeight = BigDecimal.ZERO;
        BigDecimal examWeight = BigDecimal.ZERO;
        BigDecimal caContribution = BigDecimal.ZERO;
        BigDecimal examContribution = BigDecimal.ZERO;
        boolean caComplete = true;
        boolean examComplete = true;
        int enteredMarks = 0;
        EnumSet<ResultStatus> unresolved = EnumSet.noneOf(ResultStatus.class);

        for (AssessmentInput assessment : data.assessments()) {
            validateAssessment(assessment);
            BigDecimal weight = assessment.weightPercentage();
            if (weight.signum() == 0) { continue; }
            boolean finalExam = "FINAL_EXAM".equals(assessment.assessmentType());
            if (finalExam) { examWeight = examWeight.add(weight); }
            else { caWeight = caWeight.add(weight); }

            ResultStatus missing = unresolvedStatus(assessment);
            if (missing != null) {
                unresolved.add(missing);
                if (finalExam) { examComplete = false; }
                else { caComplete = false; }
                continue;
            }
            enteredMarks++;
            BigDecimal contribution = assessment.marksObtained()
                    .multiply(weight).divide(assessment.maxMark(), PRECISION);
            if (finalExam) { examContribution = examContribution.add(contribution); }
            else { caContribution = caContribution.add(contribution); }
        }

        BigDecimal totalWeight = caWeight.add(examWeight);
        if (totalWeight.compareTo(HUNDRED) != 0) {
            throw new ResultCalculationException("Assessment weights total " + totalWeight.toPlainString()
                    + "%. Configure a complete 100% assessment plan before calculating a result.");
        }
        result.setCaMark(componentPercentage(caContribution, caWeight, caComplete));
        result.setFinalExamMark(componentPercentage(examContribution, examWeight, examComplete));
        if (!unresolved.isEmpty()) {
            if (unresolved.equals(EnumSet.of(ResultStatus.ABSENT))) {
                result.setResultStatus(ResultStatus.ABSENT);
            } else if (unresolved.equals(EnumSet.of(ResultStatus.MEDICAL))) {
                result.setResultStatus(ResultStatus.MEDICAL);
            } else if (enteredMarks == 0 && unresolved.equals(EnumSet.of(ResultStatus.PENDING))) {
                result.setResultStatus(ResultStatus.PENDING);
            } else {
                result.setResultStatus(ResultStatus.INCOMPLETE);
            }
            return result;
        }

        // Match the exact two-decimal value persisted in DECIMAL(7,2), with no component rounding first.
        BigDecimal overall = round(caContribution.add(examContribution));
        GradeRule rule;
        try { rule = gradeRuleDAO.findMatchingRule(connection, overall); }
        catch (SQLDataException error) { throw new ResultCalculationException(error.getMessage(), error); }
        if (rule == null) {
            throw new ResultCalculationException("No active grade rule matches " + overall
                    + ". Configure the grade rules before saving this result.");
        }
        result.setOverallMark(overall);
        result.setGradeRuleId(rule.getGradeRuleId());
        result.setGradeLetter(rule.getGradeLetter());
        result.setGradePoint(rule.getGradePoint());
        result.setResultStatus(rule.isPass() ? ResultStatus.PASS : ResultStatus.FAIL);
        return result;
    }

    private void validateAssessment(AssessmentInput assessment) {
        if (assessment.maxMark() == null || assessment.maxMark().signum() <= 0
                || assessment.weightPercentage() == null || assessment.weightPercentage().signum() < 0
                || assessment.weightPercentage().compareTo(HUNDRED) > 0) {
            throw new ResultCalculationException("Assessment " + assessment.assessmentId()
                    + " has an invalid maximum mark or weight.");
        }
        BigDecimal mark = assessment.marksObtained();
        if (mark != null && (mark.signum() < 0 || mark.compareTo(assessment.maxMark()) > 0)) {
            throw new ResultCalculationException("Assessment " + assessment.assessmentId()
                    + " has a mark outside 0 to " + assessment.maxMark() + ".");
        }
    }

    private ResultStatus unresolvedStatus(AssessmentInput assessment) {
        String status = assessment.markStatus();
        if (status == null || "PENDING".equals(status)) { return ResultStatus.PENDING; }
        return switch (status) {
            case "PRESENT" -> assessment.marksObtained() == null ? ResultStatus.INCOMPLETE : null;
            case "ABSENT" -> ResultStatus.ABSENT;
            case "MEDICAL" -> ResultStatus.MEDICAL;
            default -> throw new ResultCalculationException("Unknown mark status for assessment "
                    + assessment.assessmentId() + ".");
        };
    }

    private BigDecimal componentPercentage(BigDecimal contribution, BigDecimal weight, boolean complete) {
        return !complete || weight.signum() == 0 ? null
                : contribution.multiply(HUNDRED).divide(weight, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private void requirePositive(int value, String field) {
        if (value <= 0) { throw new ResultCalculationException(field + " ID must be positive."); }
    }

    private IllegalStateException systemError(String action, SQLException error) {
        return new IllegalStateException("Unable to " + action + ". Check the database connection and retry.", error);
    }

    public String describe(CourseResult result) {
        if (result == null) { return "No saved result. Calculate to check current marks and eligibility."; }
        return switch (result.getResultStatus()) {
            case PASS, FAIL -> "Grade assigned using the active database grade rule.";
            case NOT_ELIGIBLE -> "Overall eligibility is false. No final grade or grade point is assigned.";
            case PENDING -> "Awaiting a saved eligibility decision or assessment marks.";
            case ABSENT -> "An assessment is recorded as absent. No zero mark or final grade is assumed.";
            case MEDICAL -> "An assessment has medical status. No replacement mark or final grade is assumed.";
            case INCOMPLETE -> "Assessments or marks are incomplete, or unresolved assessment statuses differ.";
        };
    }
}
