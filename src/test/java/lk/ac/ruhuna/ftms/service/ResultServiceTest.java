package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseResultDAO;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.AssessmentInput;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.CalculationData;
import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.dao.GradeRuleDAO;
import lk.ac.ruhuna.ftms.exception.ResultCalculationException;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.CourseResult.ResultStatus;
import lk.ac.ruhuna.ftms.model.GradeRule;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.List;

/** Dependency-free regression runner. All grade ranges below are synthetic test fixtures. */
public final class ResultServiceTest {
    private static int checks;
    private static final List<GradeRule> TEST_RULES = List.of(
            new GradeRule(901, "TEST_FAIL", n("0"), n("49.99"), n("0.25"), false, true),
            new GradeRule(902, "TEST_PASS", n("50"), n("100"), n("3.25"), true, true));
    private static final ResultService SERVICE = service(new GradeRuleDAO() {
        @Override public GradeRule findMatchingRule(Connection connection, BigDecimal value) {
            return TEST_RULES.stream().filter(rule -> rule.getMinMark().compareTo(value) <= 0
                    && rule.getMaxMark().compareTo(value) >= 0).findFirst().orElse(null);
        }
    });

    public static void main(String[] args) throws Exception {
        List<AssessmentInput> mixedWeights = List.of(
                a("QUIZ", "50", "10", "25", "PRESENT"),
                a("PROJECT", "100", "30", "80", "PRESENT"),
                a("FINAL_EXAM", "100", "60", "60", "PRESENT"));
        CourseResult result = calculate(true, mixedWeights);
        decimal("72.50", result.getCaMark(), "weighted CA percentage");
        decimal("60.00", result.getFinalExamMark(), "exam percentage");
        decimal("65.00", result.getOverallMark(), "overall uses 40/60, not a fixed 50/50 split");
        equal(ResultStatus.PASS, result.getResultStatus(), "database is_pass controls status");
        equal("TEST_PASS", result.getGradeLetter(), "grade letter comes from rule");
        decimal("3.25", result.getGradePoint(), "grade point comes from rule");

        result = calculate(true, List.of(a("QUIZ", "100", "20", "100", "PRESENT"),
                a("FINAL_EXAM", "50", "30", "25", "PRESENT"),
                a("FINAL_EXAM", "200", "50", "120", "PRESENT")));
        decimal("56.25", result.getFinalExamMark(), "multiple exam assessments use their own weights");
        decimal("65.00", result.getOverallMark(), "multiple exam weighted overall");
        result = calculate(true, List.of(a("FINAL_EXAM", "200", "100", "99.99", "PRESENT")));
        decimal("50.00", result.getOverallMark(), "round once before grade lookup");
        equal(ResultStatus.PASS, result.getResultStatus(), "rounded boundary matches stored result");
        equal(null, result.getCaMark(), "exam-only offering has no invented CA mark");
        result = calculate(true, List.of(a("PROJECT", "100", "100", "0", "PRESENT")));
        decimal("0.00", result.getOverallMark(), "zero is a real mark");
        equal(ResultStatus.FAIL, result.getResultStatus(), "fail comes from is_pass");
        result = calculate(true, List.of(a("PROJECT", "100", "100", "100", "PRESENT")));
        decimal("100.00", result.getOverallMark(), "maximum mark");
        equal(null, result.getFinalExamMark(), "CA-only offering has no invented exam mark");

        ungraded(ResultStatus.NOT_ELIGIBLE, calculate(false, mixedWeights));
        ungraded(ResultStatus.PENDING, calculate(null, mixedWeights));
        ungraded(ResultStatus.INCOMPLETE, calculate(true, List.of()));
        ungraded(ResultStatus.PENDING, calculate(true, List.of(a("PROJECT", "100", "100", null, null))));
        ungraded(ResultStatus.PENDING, calculate(true, List.of(a("PROJECT", "100", "100", null, "PENDING"))));
        ungraded(ResultStatus.INCOMPLETE, calculate(true, List.of(a("PROJECT", "100", "100", null, "PRESENT"))));
        for (String missing : List.of("ABSENT", "MEDICAL", "PENDING")) {
            result = calculate(true, List.of(a("QUIZ", "100", "40", "75", "PRESENT"),
                    a("FINAL_EXAM", "100", "60", null, missing)));
            ungraded("PENDING".equals(missing) ? ResultStatus.INCOMPLETE : ResultStatus.valueOf(missing), result);
            decimal("75.00", result.getCaMark(), "complete component stays visible");
            equal(null, result.getFinalExamMark(), "unresolved exam stays unknown");
        }
        result = calculate(true, List.of(a("QUIZ", "100", "50", "60", "PRESENT"),
                a("PROJECT", "100", "50", null, null)));
        ungraded(ResultStatus.INCOMPLETE, result);
        equal(null, result.getCaMark(), "missing CA assessment is not excluded from the denominator");
        ungraded(ResultStatus.INCOMPLETE, calculate(true, List.of(
                a("QUIZ", "100", "40", null, "MEDICAL"),
                a("FINAL_EXAM", "100", "60", null, "ABSENT"))));
        result = calculate(true, List.of(a("QUIZ", "100", "0", null, "MEDICAL"),
                a("FINAL_EXAM", "100", "100", "70", "PRESENT")));
        decimal("70.00", result.getOverallMark(), "zero-weight assessment does not block grading");

        rejects(() -> calculate(true, List.of(a("QUIZ", "100", "40", "50", "PRESENT"))), "incomplete weights");
        rejects(() -> calculate(true, List.of(a("QUIZ", "100", "100", "101", "PRESENT"))), "mark above maximum");
        rejects(() -> calculate(true, List.of(a("QUIZ", "100", "100", "-1", "PRESENT"))), "negative mark");
        rejects(() -> calculate(true, List.of(a("QUIZ", "0", "100", "0", "PRESENT"))), "zero maximum");
        rejects(() -> calculate(true, List.of(a("QUIZ", "100", "100", "1", "UNKNOWN"))), "unknown mark status");
        rejects(() -> SERVICE.calculate(new CalculationData(1, "WITHDRAWN", true, false, mixedWeights), null), "withdrawn enrollment");
        rejects(() -> SERVICE.calculate(new CalculationData(1, "ENROLLED", true, true, mixedWeights), null), "cross-offering marks");
        rejects(() -> SERVICE.calculateResult(0), "invalid enrollment ID");
        rejects(() -> SERVICE.calculateAndSaveResult(1, -1), "invalid calculating user");
        rejects(() -> service(new GradeRuleDAO() {
            @Override public GradeRule findMatchingRule(Connection connection, BigDecimal value) { return null; }
        }).calculate(data(true, mixedWeights), null), "missing active grade rule");
        rejects(() -> service(new GradeRuleDAO() {
            @Override public GradeRule findMatchingRule(Connection connection, BigDecimal value) throws SQLException {
                throw new SQLDataException("Overlapping test ranges");
            }
        }).calculate(data(true, mixedWeights), null), "overlapping active grade rules");
        System.out.println("ResultService regression checks passed: " + checks);
    }

    private static ResultService service(GradeRuleDAO rules) {
        return new ResultService(new CourseResultDAO(), rules, new EligibilityDAO());
    }
    private static CalculationData data(Boolean eligible, List<AssessmentInput> inputs) {
        return new CalculationData(1, "ENROLLED", eligible, false, inputs);
    }
    private static CourseResult calculate(Boolean eligible, List<AssessmentInput> inputs) throws SQLException {
        return SERVICE.calculate(data(eligible, inputs), null);
    }
    private static AssessmentInput a(String type, String max, String weight, String mark, String status) {
        return new AssessmentInput(1, type, n(max), n(weight), mark == null ? null : n(mark), status);
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void decimal(String expected, BigDecimal actual, String label) {
        if (actual == null || n(expected).compareTo(actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
        checks++;
    }
    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) { throw new AssertionError(label + ": " + actual); }
        checks++;
    }
    private static void ungraded(ResultStatus expected, CourseResult result) {
        equal(expected, result.getResultStatus(), "unresolved status");
        equal(null, result.getOverallMark(), "unresolved overall mark");
        equal(null, result.getGradeRuleId(), "unresolved grade rule");
        equal(null, result.getGradeLetter(), "unresolved grade letter");
        equal(null, result.getGradePoint(), "unresolved grade point");
    }
    private static void rejects(CheckedAction action, String label) throws Exception {
        try { action.run(); }
        catch (ResultCalculationException expected) { checks++; return; }
        throw new AssertionError("Expected validation rejection: " + label);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
