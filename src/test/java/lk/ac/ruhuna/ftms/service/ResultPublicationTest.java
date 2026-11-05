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
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Explicit main runner. Real service authorization with in-memory JDBC/DAO fixtures. */
public final class ResultPublicationTest {
    private static int checks;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 19, 12, 0);

    public static void main(String[] args) throws Exception {
        try {
            productionAuthorization();
            assignedAccess();
            draftAndAudit();
            draftPublicationReasons();
            publication();
            rejectedDrafts();
            staleInputs();
            transactionFailures();
            System.out.println("Result publication checks passed: " + checks);
        } finally { SessionManager.logout(); }
    }

    private static void productionAuthorization() {
        ResultService production = new ResultService();
        for (String role : List.of("ADMIN", "STUDENT", "TECHNICAL_OFFICER")) {
            login(role);
            check(!production.canPublishResult(7), "non-lecturer cannot publish: " + role);
            rejects(SecurityException.class, () -> production.publishResult(7, null), "direct publication denied: " + role);
        }
        SessionManager.logout();
        rejects(SecurityException.class, () -> production.publishResult(7, null), "anonymous publication denied");
        rejects(SecurityException.class, () -> production.calculateAndSaveResult(1), "anonymous draft save denied");
    }

    private static void assignedAccess() throws Exception {
        Fixture f = new Fixture();
        check(f.service.canPublishResult(7), "assigned lecturer sees Publish Result");
        check(!f.service.canPublishResult(8), "another offering cannot publish");
        equal(1, f.service.getBatchResults(7).size(), "assigned lecturer can review saved drafts");
        check(!f.service.getBatchResults(7).getFirst().result().isPublished(), "publication review includes drafts");
        rejects(SecurityException.class, () -> f.service.getBatchResults(8), "unassigned draft read denied");
        f.dao.assigned = false;
        check(!f.service.canPublishResult(7), "revoked assignment disables UI hint");
        rejects(SecurityException.class, () -> f.service.publishResult(7, f.dao.saved.publicationSnapshot()), "revoked assignment rejects direct publish");
        f.dao.assigned = true;
        f.dao.actor = null;
        check(!f.service.canPublishResult(7), "inactive database account disables UI hint");
        f.dao.actor = actor("ADMIN");
        check(!f.service.canPublishResult(7), "changed database role disables UI hint");
        equal(0, f.dao.publishes, "denied callers never reach UPDATE");
        rejects(SecurityException.class, () -> f.service.calculateAndSaveResult(1), "changed actor role denies draft save");
    }

    private static void draftAndAudit() throws Exception {
        Fixture f = new Fixture("LECTURER");
        CourseResult preview = f.service.calculateResult(1);
        check(preview.getResultId() == 0 && !preview.isPublished(), "calculate remains an unsaved preview");
        equal(0, f.dao.saves, "preview performs no upsert");
        f = new Fixture("LECTURER");
        Fixture forged = f;
        rejects(SecurityException.class, () -> forged.service.calculateAndSaveResult(1, 999), "forged calculator rejected");
        equal(0, f.opens, "forged identity rejected before opening DB");
        f.dao.saved.setPublished(true);
        f.dao.saved.setPublishedAt(TIME);
        Fixture publishedFixture=f;
        rejects(ResultCalculationException.class, () -> publishedFixture.service.calculateAndSaveResult(1), "published overwrite rejected");
        f.dao.saved.setPublished(false);f.dao.saved.setPublishedAt(null);
        CourseResult saved = f.service.calculateAndSaveResult(1);
        check(!saved.isPublished() && saved.getPublishedAt() == null, "saving unpublished input remains a draft");
        equal(12, saved.getCalculatedBy(), "calculator comes from login");
        equal(1, f.commits, "draft committed");
        equal(Connection.TRANSACTION_REPEATABLE_READ, f.isolation, "existing draft transaction retained");
        f = new Fixture("LECTURER");
        f.dao.actor = null;
        Fixture inactive = f;
        rejects(SecurityException.class, () -> inactive.service.calculateAndSaveResult(1, 12), "inactive calculator rejected");
        equal(0, f.dao.saves, "inactive actor performs no save");
        equal(1, f.rollbacks, "inactive save rolled back");
    }

    private static void publication() throws Exception {
        for (String mark : List.of("75", "0")) {
            Fixture f = new Fixture();
            f.dao.data = data(true, mark, "PRESENT", "100");
            f.dao.saved = f.savedCalculation();
            var before = f.dao.saved.publicationSnapshot();
            CourseResult published = f.service.publishResult(7, before);
            check(published.isPublished() && published.getPublishedAt() != null, "publication flags set together");
            equal(mark.equals("0") ? ResultStatus.FAIL : ResultStatus.PASS, published.getResultStatus(), "PASS and zero-mark FAIL publish");
            equal(before.calculatedBy(), published.getCalculatedBy(), "publication preserves calculator");
            equal(before.calculatedAt(), published.getCalculatedAt(), "publication preserves calculation time");
            equal(before.overallMark(), published.getOverallMark(), "publication does not replace reviewed marks");
            equal(1, f.commits, "publication committed once");
            equal(0, f.rollbacks, "successful publication not rolled back");
            equal(Connection.TRANSACTION_SERIALIZABLE, f.isolation, "input reads are serializable");
            check(f.closed && f.dao.locked, "connection closed and draft row locked");
            rejects(ResultCalculationException.class, () -> f.service.publishResult(7, before), "repeat publication rejected");
        }
        Fixture lecturer = new Fixture();
        login("LECTURER");
        lecturer.dao.actor = actor("LECTURER");
        lecturer.service.publishResult(7, lecturer.dao.saved.publicationSnapshot());
        equal(1, lecturer.dao.assignmentChecks, "approved test lecturer must have an assignment");
    }

    private static void rejectedDrafts() throws Exception {
        for (ResultStatus status : List.of(ResultStatus.PENDING, ResultStatus.INCOMPLETE, ResultStatus.ABSENT,
                ResultStatus.MEDICAL, ResultStatus.NOT_ELIGIBLE)) {
            rejectsDraft(r -> r.setResultStatus(status), "unresolved draft: " + status);
        }
        rejectsDraft(r -> r.setGradeLetter(null), "missing letter");
        rejectsDraft(r -> r.setGradeLetter(" "), "blank letter");
        rejectsDraft(r -> r.setGradePoint(null), "missing point");
        rejectsDraft(r -> r.setGradePoint(n("-1")), "negative point");
        rejectsDraft(r -> r.setGradeRuleId(null), "missing rule");
        rejectsDraft(r -> r.setGradeRuleId(0), "invalid rule ID");
        rejectsDraft(r -> r.setOverallMark(null), "missing overall");
        rejectsDraft(r -> r.setOverallMark(n("101")), "invalid overall");
        rejectsDraft(r -> r.setOverallMark(n("-1")), "negative overall");
        rejectsDraft(r -> r.setCalculatedBy(null), "legacy unaudited draft requires resave");
        rejectsDraft(r -> r.setCalculatedBy(0), "invalid calculator ID");
        rejectsDraft(r -> r.setCalculatedAt(null), "missing audit timestamp");
        rejectsDraft(r -> r.setPublishedAt(TIME), "inconsistent publication fields");
        rejectsDraft(r -> r.setResultId(0), "unsaved preview");
        Fixture missing = new Fixture();
        var reviewed = missing.dao.saved.publicationSnapshot();
        missing.dao.saved = null;
        rejectPublication(missing, () -> missing.service.publishResult(7, reviewed), "missing saved draft");
        Fixture wrongOffering = new Fixture();
        wrongOffering.dao.contextMatches = false;
        rejectPublication(wrongOffering, () -> wrongOffering.service.publishResult(7,
                wrongOffering.dao.saved.publicationSnapshot()), "wrong/withdrawn/cancelled enrollment context");
        Fixture changed = new Fixture();
        var old = changed.dao.saved.publicationSnapshot();
        changed.dao.saved.setCalculatedAt(TIME.plusSeconds(1));
        rejectPublication(changed, () -> changed.service.publishResult(7, old), "changed reviewed version");
        Fixture changedGrade = new Fixture();
        var oldGrade = changedGrade.dao.saved.publicationSnapshot();
        changedGrade.dao.saved.setOverallMark(n("70"));
        rejectPublication(changedGrade, () -> changedGrade.service.publishResult(7, oldGrade), "same-second result edit");
    }

    private static void draftPublicationReasons() throws Exception {
        Fixture f = new Fixture();
        equal(null, ResultService.draftPublicationBlockReason(f.dao.saved), "complete PASS has no draft block");
        f.dao.saved.setResultStatus(ResultStatus.FAIL);
        equal(null, ResultService.draftPublicationBlockReason(f.dao.saved), "complete FAIL has no draft block");
        f.dao.saved.setResultStatus(ResultStatus.PASS);
        f.dao.saved.setCalculatedBy(null);
        var before = f.dao.saved.publicationSnapshot();
        String reason = ResultService.draftPublicationBlockReason(f.dao.saved);
        check(reason.contains("calculating lecturer") && reason.contains("Save / Update Result"),
                "legacy PASS draft gives an actionable audit explanation");
        equal(before, f.dao.saved.publicationSnapshot(), "draft hint never fills in missing audit metadata");
        equal(0, f.opens, "draft hint does not open a database connection");
        try {
            f.service.publishResult(7, before);
            throw new AssertionError("Unaudited draft published");
        } catch (ResultCalculationException expected) {
            equal(reason, expected.getMessage(), "UI hint and publication enforce the same audit rule");
        }
        equal(0, f.dao.publishes, "unaudited draft never reaches publication");
        equal(0, f.dao.saves, "publication never silently repairs an unaudited draft");
        equal(0, f.commits, "unaudited publication cannot commit");
        f.dao.saved.setPublished(true);
        check(ResultService.draftPublicationBlockReason(f.dao.saved).contains("already published"),
                "published protection takes priority over missing audit fields");
        check(ResultService.draftPublicationBlockReason(null).contains("No saved draft"), "missing draft explained");
    }

    private static void staleInputs() throws Exception {
        for (CalculationData data : List.of(data(true, "80", "PRESENT", "100"),
                data(null, "75", "PRESENT", "100"), data(false, "75", "PRESENT", "100"),
                data(true, null, "PENDING", "100"), data(true, null, "MEDICAL", "100"),
                data(true, "75", "PRESENT", "90"), data(true, "101", "PRESENT", "100"),
                new CalculationData(1, "WITHDRAWN", true, false, List.of()),
                new CalculationData(1, "ENROLLED", true, true, List.of()),
                new CalculationData(1, "ENROLLED", true, false, List.of()))) {
            Fixture f = new Fixture();
            f.dao.data = data;
            rejectPublication(f, () -> f.service.publishResult(7, f.dao.saved.publicationSnapshot()), "changed/invalid calculation inputs");
        }
        Fixture rules = new Fixture();
        rules.gradePoint = n("3.50");
        rejectPublication(rules, () -> rules.service.publishResult(7, rules.dao.saved.publicationSnapshot()), "changed grade rule");
        Fixture absentRule = new Fixture();
        absentRule.noRule = true;
        rejectPublication(absentRule, () -> absentRule.service.publishResult(7, absentRule.dao.saved.publicationSnapshot()), "removed active rule");
    }

    private static void transactionFailures() throws Exception {
        Fixture denied = new Fixture();
        login("LECTURER");
        denied.dao.actor = actor("LECTURER");
        denied.dao.assigned = false;
        rejects(SecurityException.class, () -> denied.service.publishResult(7, denied.dao.saved.publicationSnapshot()), "unassigned lecturer denied");
        equal(0, denied.dao.publishes, "unassigned lecturer cannot mutate");
        Fixture inactive = new Fixture();
        inactive.dao.actor = null;
        rejects(SecurityException.class, () -> inactive.service.publishResult(7, inactive.dao.saved.publicationSnapshot()), "deactivated publisher denied");
        Fixture changedRole = new Fixture();
        changedRole.dao.actor = actor("STUDENT");
        rejects(SecurityException.class, () -> changedRole.service.publishResult(7, changedRole.dao.saved.publicationSnapshot()), "changed DB role denied");
        Fixture sql = new Fixture();
        sql.dao.failPublish = true;
        rejects(IllegalStateException.class, () -> sql.service.publishResult(7, sql.dao.saved.publicationSnapshot()), "SQL failure propagated");
        equal(1, sql.rollbacks, "SQL failure rolled back");
        equal(0, sql.commits, "SQL failure not committed");
        check(!sql.dao.saved.isPublished() && sql.dao.saved.getPublishedAt() == null, "failed publication restored draft");
        Fixture logout = new Fixture();
        logout.dao.afterPublish = () -> login("LECTURER"); // same user, new session generation
        rejects(SecurityException.class, () -> logout.service.publishResult(7, logout.dao.saved.publicationSnapshot()), "session replacement before commit denied");
        equal(1, logout.rollbacks, "session replacement rolled back");
        check(!logout.dao.saved.isPublished(), "session change did not publish");
    }

    private static void rejectsDraft(Consumer<CourseResult> change, String label) throws Exception {
        Fixture f = new Fixture();
        change.accept(f.dao.saved);
        check(ResultService.draftPublicationBlockReason(f.dao.saved) != null, label + " disables the UI hint");
        rejects(ResultCalculationException.class, () -> f.service.publishResult(7, f.dao.saved.publicationSnapshot()), label);
        equal(0, f.dao.publishes, label + " cannot publish");
    }

    private static void rejectPublication(Fixture f, Action action, String label) {
        rejects(ResultCalculationException.class, action, label);
        equal(0, f.dao.publishes, label + " cannot publish");
        equal(0, f.commits, label + " cannot commit");
        equal(1, f.rollbacks, label + " rolls back");
    }

    private static final class Fixture {
        final FakeDAO dao = new FakeDAO();
        final ResultService service;
        int opens, commits, rollbacks, isolation;
        boolean closed, autoCommit = true, noRule;
        BigDecimal gradePoint = n("3.25");
        Fixture() throws SQLException { this("LECTURER"); }
        Fixture(String role) throws SQLException {
            login(role);
            dao.actor = actor(role);
            service = new ResultService(dao, new GradeRuleDAO() {
                @Override public GradeRule findMatchingRule(Connection c, BigDecimal mark) {
                    return noRule ? null : new GradeRule(90, mark.signum() == 0 ? "TEST_FAIL" : "TEST_PASS",
                            n("0"), n("100"), gradePoint, mark.signum() > 0, true);
                }
            }, new EligibilityDAO(), () -> {
                opens++;
                CourseResult before = copy(dao.saved);
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "setTransactionIsolation" -> isolation = (int) args[0];
                                case "setAutoCommit" -> autoCommit = (boolean) args[0];
                                case "getAutoCommit" -> { return autoCommit; }
                                case "commit" -> commits++;
                                case "rollback" -> { rollbacks++; dao.saved = before; }
                                case "close" -> closed = true;
                                default -> throw new AssertionError("Unexpected JDBC call: " + method.getName());
                            }
                            return null;
                        });
            });
            dao.saved = savedCalculation();
        }
        CourseResult savedCalculation() throws SQLException {
            CourseResult result = service.calculate(dao.data, null);
            result.setResultId(11);
            result.setCalculatedAt(TIME);
            result.setCalculatedBy(12);
            return result;
        }
    }

    private static final class FakeDAO extends CourseResultDAO {
        CourseResult saved;
        CalculationData data = data(true, "75", "PRESENT", "100");
        User actor = actor("LECTURER");
        boolean contextMatches = true, assigned = true, locked, failPublish;
        int saves, publishes, assignmentChecks;
        Runnable afterPublish = () -> {};
        @Override public int enrollmentOffering(Connection c, int enrollment) { return 7; }
        @Override public boolean eligibilityInputsCurrent(Connection c,int enrollment) { return true; }
        @Override public boolean draftInputsCurrent(Connection c,int enrollment,java.time.LocalDateTime calculatedAt) { return true; }
        @Override public void invalidateStoredGpa(Connection c,int enrollment) {}
        @Override public User lockActiveActor(Connection c, int id) { return actor; }
        @Override public boolean lockLecturerAssignment(Connection c, int id, int offering) {
            assignmentChecks++;
            return assigned && id == 12 && offering == 7;
        }
        @Override public List<lk.ac.ruhuna.ftms.model.ResultRow> findEnrollmentRowsByOfferingId(Connection c, int offering) {
            return List.of(new lk.ac.ruhuna.ftms.model.ResultRow(1, "TEST/1", "Student", 1, saved));
        }
        @Override public boolean lockPublicationContext(Connection c, int enrollment, int offering) {
            return contextMatches && enrollment == 1 && offering == 7;
        }
        @Override public CourseResult getByEnrollmentId(Connection c, int enrollment, boolean lock) { locked = lock; return saved; }
        @Override public CalculationData loadCalculationData(Connection c, int enrollment, boolean lock) { return data; }
        @Override public CourseResult saveOrUpdate(Connection c, CourseResult result) {
            saves++;
            saved = result;
            saved.setResultId(11);
            saved.setCalculatedAt(TIME);
            return saved;
        }
        @Override public CourseResult publishDraft(Connection c, int enrollment, int resultId) throws SQLException {
            publishes++;
            saved.setPublished(true);
            saved.setPublishedAt(TIME.plusSeconds(1));
            afterPublish.run();
            if (failPublish) { throw new SQLException("test failure after UPDATE"); }
            return saved;
        }
    }

    private static CourseResult copy(CourseResult source) {
        if (source == null) { return null; }
        CourseResult result = new CourseResult();
        result.setResultId(source.getResultId()); result.setEnrollmentId(source.getEnrollmentId());
        result.setCaMark(source.getCaMark()); result.setFinalExamMark(source.getFinalExamMark());
        result.setOverallMark(source.getOverallMark()); result.setGradeRuleId(source.getGradeRuleId());
        result.setGradeLetter(source.getGradeLetter()); result.setGradePoint(source.getGradePoint());
        result.setResultStatus(source.getResultStatus()); result.setPublished(source.isPublished());
        result.setCalculatedAt(source.getCalculatedAt()); result.setCalculatedBy(source.getCalculatedBy());
        result.setPublishedAt(source.getPublishedAt());
        return result;
    }
    private static CalculationData data(Boolean eligible, String mark, String status, String weight) {
        return new CalculationData(1, "ENROLLED", eligible, false, List.of(
                new AssessmentInput(1, "FINAL_EXAM", n("100"), n(weight), mark == null ? null : n(mark), status)));
    }
    private static User actor(String role) { return new User(12, "test", "Test", null, role, true); }
    private static void login(String role) { SessionManager.startSession(actor(role)); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void equal(Object expected, Object actual, String label) { check(Objects.equals(expected, actual), label + ": " + actual); }
    private static void rejects(Class<? extends Throwable> type, Action action, String label) {
        try { action.run(); } catch (Throwable error) {
            if (type.isInstance(error)) { checks++; return; }
            throw new AssertionError(label + ": unexpected failure", error);
        }
        throw new AssertionError("Expected rejection: " + label);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
