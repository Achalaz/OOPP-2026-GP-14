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
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.model.CourseResult.PublicationSnapshot;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

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
    public List<CourseResultDAO.PublishedStudentResult> getMyPublishedResults(SessionIdentity identity)
            throws SQLException {
        return new StudentAccessService(identity).read(resultDAO::findPublishedForStudent);
    }
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final CourseResultDAO resultDAO;
    private final GradeRuleDAO gradeRuleDAO;
    private final EligibilityDAO eligibilityDAO;
    private final ConnectionSource connections;
    private final PublicationAuthority publicationAuthority;

    public static final String PUBLICATION_POLICY_PENDING =
            "Only an active lecturer assigned to this offering may publish results.";

    @FunctionalInterface interface ConnectionSource { Connection open() throws SQLException; }
    @FunctionalInterface interface PublicationAuthority { boolean permits(User user, int offeringId); }

    public ResultService() {
        this(new CourseResultDAO(), new GradeRuleDAO(), new EligibilityDAO());
    }

    public ResultService(CourseResultDAO resultDAO, GradeRuleDAO gradeRuleDAO, EligibilityDAO eligibilityDAO) {
        this(resultDAO, gradeRuleDAO, eligibilityDAO, DBConnection::getConnection);
    }

    ResultService(CourseResultDAO resultDAO, GradeRuleDAO gradeRuleDAO, EligibilityDAO eligibilityDAO,
                  ConnectionSource connections) {
        this(resultDAO, gradeRuleDAO, eligibilityDAO, connections,
                (user, offering) -> "LECTURER".equals(user.getRole()));
    }

    /** Package-only seams for transaction tests; no runtime setting enables an unapproved role. */
    ResultService(CourseResultDAO resultDAO, GradeRuleDAO gradeRuleDAO, EligibilityDAO eligibilityDAO,
                  ConnectionSource connections, PublicationAuthority publicationAuthority) {
        this.resultDAO = Objects.requireNonNull(resultDAO);
        this.gradeRuleDAO = Objects.requireNonNull(gradeRuleDAO);
        this.eligibilityDAO = Objects.requireNonNull(eligibilityDAO);
        this.connections = Objects.requireNonNull(connections);
        this.publicationAuthority = Objects.requireNonNull(publicationAuthority);
    }

    public List<EligibilityOffering> getOfferings() {
        try { return new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO()
                .findAssigned(new LecturerAccessService().requireLecturer()); }
        catch (SQLException error) { throw systemError("load course offerings", error); }
    }

    public List<ResultRow> getBatchResults(int offeringId) {
        requirePositive(offeringId, "Course offering");
        SessionIdentity identity = SessionManager.requireSession();
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                requireAssigned(c, identity, offeringId);
                var rows = resultDAO.findEnrollmentRowsByOfferingId(c, offeringId);
                SessionManager.requireSession(identity);
                c.commit();
                return rows;
            } catch(SQLException | RuntimeException error) {
                try { c.rollback(); } catch(SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
        catch (SQLException error) { throw systemError("load batch results", error); }
    }

    public CourseResult getResultByEnrollmentId(int enrollmentId) {
        requirePositive(enrollmentId, "Enrollment");
        try {
            LecturerAccessService access = new LecturerAccessService();
            int offering = new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO().enrollmentOffering(enrollmentId);
            access.requireOffering(offering);
            var result=resultDAO.getByEnrollmentId(enrollmentId);
            access.requireOffering(offering);
            return result;
        }
        catch (SQLException error) { throw systemError("load the saved result", error); }
    }

    public CourseResult calculateResult(int enrollmentId) {
        requirePositive(enrollmentId, "Enrollment");
        return calculateInTransaction(enrollmentId, SessionManager.requireSession(), false);
    }

    public CourseResult calculateAndSaveResult(int enrollmentId) {
        return calculateAndSaveResult(enrollmentId, null);
    }

    /** Recalculates from current database inputs; never trusts a UI preview or a supplied grade. */
    public CourseResult calculateAndSaveResult(int enrollmentId, Integer calculatedBy) {
        if (calculatedBy != null) { requirePositive(calculatedBy, "Calculating user"); }
        SessionIdentity identity = SessionManager.requireSession();
        if (calculatedBy != null && calculatedBy != identity.userId()) {
            throw new SecurityException("Calculated-by must be the signed-in user.");
        }
        requirePublicationAuthority(SessionManager.getCurrentUser(), 1);
        return calculateInTransaction(enrollmentId, identity, true);
    }

    private CourseResult calculateInTransaction(int enrollmentId, SessionIdentity identity, boolean save) {
        requirePositive(enrollmentId, "Enrollment");
        try (Connection connection = connections.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                int offering = resultDAO.enrollmentOffering(connection, enrollmentId);
                requireAssigned(connection, identity, offering);
                if (!resultDAO.lockPublicationContext(connection, enrollmentId, offering))
                    throw new ResultCalculationException("Enrollment or offering is unavailable.");
                CourseResult existing = resultDAO.getByEnrollmentId(connection, enrollmentId, true);
                if (existing != null && existing.isPublished())
                    throw new ResultCalculationException("Published results are protected; a formal correction policy is required.");
                CalculationData data = resultDAO.loadCalculationData(connection, enrollmentId, save);
                if (data == null) {
                    throw new ResultCalculationException("Enrollment " + enrollmentId + " does not exist.");
                }
                CourseResult result = calculate(data, connection);
                result.setCalculatedBy(identity == null ? null : identity.userId());
                // Recalculated results are drafts. A separate publication workflow must approve them.
                result.setPublished(false);
                result.setPublishedAt(null);
                if (save) { result = resultDAO.saveOrUpdate(connection, result); }
                SessionManager.requireSession(identity);
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

    /** UI hint only. publishResult independently enforces authorization under the transaction. */
    public boolean canPublishResult(int offeringId) {
        User user = SessionManager.getCurrentUser();
        if (offeringId <= 0 || user == null || !user.isActive() || !"LECTURER".equals(user.getRole())) return false;
        try (Connection c = connections.open()) {
            requireAssigned(c, SessionManager.requireSession(), offeringId);
            return true;
        } catch (SQLException | SecurityException error) { return false; }
    }

    private void requireAssigned(Connection c, SessionIdentity identity, int offering) throws SQLException {
        requirePublicationAuthority(requireActiveActor(c, identity), offering);
        if (!resultDAO.lockLecturerAssignment(c, identity.userId(), offering))
            throw new SecurityException("You are not assigned to this course offering.");
    }

    public CourseResult publishResult(int offeringId, PublicationSnapshot reviewed) {
        SessionIdentity identity = SessionManager.requireSession();
        requirePublicationAuthority(SessionManager.getCurrentUser(), offeringId);
        requirePositive(offeringId, "Course offering");
        if (reviewed == null || reviewed.resultId() <= 0 || reviewed.enrollmentId() <= 0) {
            throw new ResultCalculationException("Save and review a draft before publishing.");
        }
        try (Connection connection = connections.open()) {
            // InnoDB SERIALIZABLE also locks the input/rule ranges read below, including missing marks.
            // An enrollment lock alone does not prevent concurrent assessment, mark or grade-rule edits.
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                User actor = requireActiveActor(connection, identity);
                requirePublicationAuthority(actor, offeringId);
                if ("LECTURER".equals(actor.getRole())
                        && !resultDAO.lockLecturerAssignment(connection, identity.userId(), offeringId)) {
                    throw new SecurityException("You are not assigned to this course offering.");
                }
                if (!resultDAO.lockPublicationContext(connection, reviewed.enrollmentId(), offeringId)) {
                    throw new ResultCalculationException("Enrollment and offering must match and must not be withdrawn or cancelled.");
                }
                CourseResult saved = resultDAO.getByEnrollmentId(connection, reviewed.enrollmentId(), true);
                requirePublishableDraft(saved);
                if (!saved.publicationSnapshot().equals(reviewed)) {
                    throw new ResultCalculationException("The saved draft has changed. Refresh and review it before publishing.");
                }
                CalculationData data = resultDAO.loadCalculationData(connection, reviewed.enrollmentId(), true);
                if (data == null || data.enrollmentId() != reviewed.enrollmentId()) {
                    throw new ResultCalculationException("The enrollment is no longer available.");
                }
                CourseResult current = calculate(data, connection);
                if (!resultDAO.eligibilityInputsCurrent(connection, reviewed.enrollmentId()))
                    throw new ResultCalculationException("Eligibility is stale or attendance is unresolved. Re-evaluate eligibility before publishing.");
                if (data.assessments().stream().anyMatch(a -> !"FINAL_EXAM".equals(a.assessmentType()) && a.weightPercentage().signum() > 0)) {
                    var ca = EligibilityService.calculateCA(data.assessments());
                    if (!Boolean.TRUE.equals(ca.eligible()))
                        throw new ResultCalculationException("Current CA does not confirm eligibility. Re-evaluate eligibility before publishing.");
                }
                if (!sameCalculatedResult(saved, current)) {
                    throw new ResultCalculationException("The draft is stale. Recalculate, save and review the current result before publishing.");
                }
                CourseResult published = resultDAO.publishDraft(connection, reviewed.enrollmentId(), reviewed.resultId());
                resultDAO.invalidateStoredGpa(connection, reviewed.enrollmentId());
                SessionManager.requireSession(identity);
                connection.commit();
                return published;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        } catch (SQLException error) { throw systemError("publish the result", error); }
    }

    private User requireActiveActor(Connection connection, SessionIdentity identity) throws SQLException {
        SessionManager.requireSession(identity);
        User actor = resultDAO.lockActiveActor(connection, identity.userId());
        User sessionUser = SessionManager.getCurrentUser();
        if (actor == null || !actor.isActive() || sessionUser == null
                || !Objects.equals(actor.getRole(), sessionUser.getRole())) {
            throw new SecurityException("Your account is inactive or its role has changed. Sign in again.");
        }
        SessionManager.requireSession(identity);
        return actor;
    }

    private void requirePublicationAuthority(User user, int offeringId) {
        if (user == null || !user.isActive() || !"LECTURER".equals(user.getRole()) || !publicationAuthority.permits(user, offeringId)) {
            throw new SecurityException(PUBLICATION_POLICY_PENDING);
        }
    }

    private void requirePublishableDraft(CourseResult saved) {
        if (saved == null) { throw new ResultCalculationException("No saved draft exists for this enrollment."); }
        if (saved.isPublished()) { throw new ResultCalculationException("This result is already published. Refresh to view it."); }
        if (saved.getPublishedAt() != null || saved.getCalculatedAt() == null || saved.getCalculatedBy() == null
                || saved.getCalculatedBy() <= 0 || saved.getResultId() <= 0
                || !(saved.getResultStatus() == ResultStatus.PASS || saved.getResultStatus() == ResultStatus.FAIL)
                || saved.getOverallMark() == null || saved.getOverallMark().signum() < 0
                || saved.getOverallMark().compareTo(HUNDRED) > 0 || saved.getGradeRuleId() == null
                || saved.getGradeRuleId() <= 0 || saved.getGradeLetter() == null || saved.getGradeLetter().isBlank()
                || saved.getGradePoint() == null || saved.getGradePoint().signum() < 0) {
            throw new ResultCalculationException("Only a complete, audited PASS or FAIL draft can be published. Recalculate and save it first.");
        }
    }

    private boolean sameCalculatedResult(CourseResult saved, CourseResult current) {
        return saved.getEnrollmentId() == current.getEnrollmentId()
                && saved.getResultStatus() == current.getResultStatus()
                && Objects.equals(saved.getGradeRuleId(), current.getGradeRuleId())
                && Objects.equals(saved.getGradeLetter(), current.getGradeLetter())
                && sameDecimal(saved.getCaMark(), current.getCaMark())
                && sameDecimal(saved.getFinalExamMark(), current.getFinalExamMark())
                && sameDecimal(saved.getOverallMark(), current.getOverallMark())
                && sameDecimal(saved.getGradePoint(), current.getGradePoint());
    }

    private boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
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
