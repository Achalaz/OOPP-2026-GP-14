package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.CourseResult.ResultStatus;
import lk.ac.ruhuna.ftms.service.ResultService;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

/** Explicit local integration runner; optional write checks are always rolled back. */
public final class ResultDatabaseSmokeTest {
    public static void main(String[] args) throws Exception {
        var actor=new UserDAO().getAllUsers().stream().filter(u->u.isActive() && "LECTURER".equals(u.getRole())).findFirst().orElseThrow();
        lk.ac.ruhuna.ftms.util.SessionManager.startSession(actor);
        GradeRuleDAO grades = new GradeRuleDAO();
        CourseResultDAO results = new CourseResultDAO();
        ResultService service = new ResultService();
        int rulesChecked = 0;
        for (var rule : grades.findActiveRules()) {
            check(grades.findMatchingRule(rule.getMinMark()).getGradeRuleId() == rule.getGradeRuleId(), "lower grade boundary");
            check(grades.findMatchingRule(rule.getMaxMark()).getGradeRuleId() == rule.getGradeRuleId(), "upper grade boundary");
            rulesChecked++;
        }
        int enrollmentsChecked = 0;
        for (var offering : service.getOfferings()) {
            var batch = service.getBatchResults(offering.offeringId());
            var saved = results.findByOfferingId(offering.offeringId());
            check(batch.stream().filter(row -> row.result() != null).count() == saved.size(), "batch includes saved and unsaved enrollments");
            for (var row : batch) {
                if (row.result() != null) {
                    check(snapshot(row.result()).equals(snapshot(service.getResultByEnrollmentId(row.enrollmentId()))), "individual and batch mapping");
                }
                CourseResult calculated = row.result()!=null && row.result().isPublished() ? row.result() : service.calculateResult(row.enrollmentId());
                boolean graded = calculated.getResultStatus() == ResultStatus.PASS || calculated.getResultStatus() == ResultStatus.FAIL;
                check(graded == (calculated.getGradeRuleId() != null), "grade rule only for graded results");
                check(graded == (calculated.getGradePoint() != null), "grade point only for graded results");
                enrollmentsChecked++;
            }
        }
        System.out.println("Database read checks passed: " + rulesChecked + " grade rules, " + enrollmentsChecked + " enrollments.");

        if (!Arrays.asList(args).contains("--rollback-write")) { return; }
        int enrollmentId;
        List<Object> original;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("SELECT enrollment_id FROM course_results WHERE published=FALSE ORDER BY result_id LIMIT 1 FOR UPDATE");
                     var rows = statement.executeQuery()) {
                    check(rows.next(), "an existing result is required for the rollback test");
                    enrollmentId = rows.getInt(1);
                }
                CourseResult before = results.getByEnrollmentId(connection, enrollmentId);
                original = snapshot(before);
                CourseResult ungraded = new CourseResult();
                ungraded.setEnrollmentId(enrollmentId);
                ungraded.setResultStatus(ResultStatus.NOT_ELIGIBLE);
                CourseResult updated = results.saveOrUpdate(connection, ungraded);
                check(updated.getResultId() == before.getResultId(), "upsert updates the existing row");
                check(updated.getGradeRuleId() == null && updated.getGradeLetter() == null
                        && updated.getGradePoint() == null && updated.getOverallMark() == null, "upsert clears old grade fields");
                check(!updated.isPublished() && updated.getPublishedAt() == null, "updated draft is unpublished");
                check(updated.getCalculatedAt() != null && updated.getCalculatedBy() == null, "nullable audit mapping");
                CourseResult restored = results.saveOrUpdate(connection, before);
                check(restored.getResultId() == before.getResultId(), "repeated upsert keeps one result");
                check(java.util.Objects.equals(restored.getGradePoint(), before.getGradePoint()), "decimal grade mapping");
            } finally { connection.rollback(); }
        }
        check(original.equals(snapshot(results.getByEnrollmentId(enrollmentId))), "rollback restores all result fields");
        System.out.println("Database upsert checks passed; all result row changes rolled back.");
    }

    private static List<Object> snapshot(CourseResult value) {
        return Arrays.asList(value.getResultId(), value.getEnrollmentId(), value.getCaMark(), value.getFinalExamMark(),
                value.getOverallMark(), value.getGradeRuleId(), value.getGradeLetter(), value.getGradePoint(),
                value.getResultStatus(), value.isPublished(), value.getCalculatedAt(), value.getPublishedAt(), value.getCalculatedBy());
    }
    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
