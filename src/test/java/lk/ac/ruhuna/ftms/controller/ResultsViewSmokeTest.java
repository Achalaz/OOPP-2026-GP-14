package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.CourseResult.ResultStatus;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.ResultRow;
import lk.ac.ruhuna.ftms.service.ResultService;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Loads the real FXML with an in-memory service; no database writes or visible window. */
public final class ResultsViewSmokeTest {
    private static Parent root;
    private static final InMemoryService service = new InMemoryService();
    private static boolean confirm;
    private static int confirmations;

    public static void main(String[] args) throws Exception {
        SessionManager.startSession(new User(12, "test", "Test", null, "LECTURER", true));
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) { throw new AssertionError("JavaFX startup timed out"); }
        try {
            checkSavedDraftSelections();
            onFx(() -> {
                FXMLLoader loader = new FXMLLoader(ResultsViewSmokeTest.class.getResource("/fxml/results.fxml"));
                loader.setControllerFactory(type -> new ResultsController(service) {
                    @Override boolean confirmPublication(ResultRow row, CourseResult.PublicationSnapshot reviewed) {
                        check(service.publications == 0, "confirmation precedes service publication");
                        check(reviewed.resultId() == 1 && reviewed.enrollmentId() == row.enrollmentId(), "confirmation targets saved selection");
                        confirmations++;
                        return confirm;
                    }
                });
                root = loader.load();
                new Scene(root, 1160, 780);
                root.applyCss();
                root.layout();
                return null;
            });
            await(() -> !combo("offeringCombo").isDisabled());
            onFx(() -> { combo("offeringCombo").getSelectionModel().selectFirst(); return null; });
            await(() -> !combo("enrollmentCombo").getItems().isEmpty());
            onFx(() -> { combo("enrollmentCombo").getSelectionModel().selectFirst(); return null; });
            onFx(() -> {
                check(button("saveButton").isDisabled(), "save requires calculation");
                check(!button("publishButton").isVisible() && !button("publishButton").isManaged()
                        && button("publishButton").isDisabled(), "publication hidden without authority");
                button("calculateButton").fire();
                return null;
            });
            await(() -> !button("saveButton").isDisabled());
            onFx(() -> {
                check(label("overallMarkLabel").getText().equals("65.00"), "individual mark display");
                check(label("gradeLetterLabel").getText().equals("TEST"), "individual grade display");
                check(label("resultStatusLabel").getText().equals("PASS"), "individual status display");
                button("saveButton").fire();
                return null;
            });
            await(() -> label("statusLabel").getText().startsWith("Unpublished result saved"));
            onFx(() -> {
                check(label("resultStateLabel").getText().contains("Saved draft"), "saved state display");
                service.authorized = true; // test permission only; production remains closed
                check(((TableView<?>) root.lookup("#batchTable")).getItems().size() == 1, "batch row retained");
                button("refreshButton").fire();
                return null;
            });
            await(() -> !button("refreshButton").isDisabled());
            onFx(() -> {
                combo("enrollmentCombo").getSelectionModel().selectFirst();
                check(label("resultStateLabel").getText().contains("Saved draft"), "refresh reloads persisted result");
                check(button("publishButton").isVisible() && !button("publishButton").isDisabled(), "authorized saved draft can request publication");
                button("publishButton").fire();
                check(confirmations == 1 && service.publications == 0, "cancel does not publish");
                confirm = true;
                button("publishButton").fire();
                check(button("publishButton").isDisabled(), "publication disables repeated clicks while busy");
                return null;
            });
            await(() -> label("statusLabel").getText().startsWith("Result published"));
            onFx(() -> {
                check(confirmations == 2 && service.publications == 1, "confirmed action publishes once");
                check(label("resultStateLabel").getText().contains("Published"), "published state displayed");
                check(button("publishButton").isDisabled(), "published row cannot publish again");
                button("clearButton").fire();
                check(combo("enrollmentCombo").getItems().isEmpty(), "clear empties batch");
                check(button("saveButton").isDisabled(), "clear disables saving");
                return null;
            });
            service.waitForLoad = true;
            onFx(() -> { combo("offeringCombo").getSelectionModel().selectFirst(); return null; });
            check(service.entered.await(5,TimeUnit.SECONDS),"background result read started");
            SessionManager.startSession(new User(12,"test","Test",null,"LECTURER",true));
            service.release.countDown();
            await(() -> !button("refreshButton").isDisabled());
            onFx(() -> {
                check(combo("enrollmentCombo").getItems().isEmpty(),"replaced login cannot receive late result rows");
                check(label("statusLabel").getText().contains("session has ended"),"expired result screen explains reauthentication");
                return null;
            });
            checkGpaStaleSession();
            System.out.println("Results FXML and controller smoke checks passed.");
        } finally { Platform.exit(); SessionManager.logout(); }
    }

    /** Reproduces ICT2122's published, unaudited PASS draft and PENDING rows without a database. */
    private static void checkSavedDraftSelections() throws Exception {
        CourseResult published = selectionResult(101, ResultStatus.PASS);
        published.setPublished(true);
        published.setPublishedAt(published.getCalculatedAt());
        CourseResult draft = selectionResult(102, ResultStatus.PASS);
        draft.setCalculatedBy(null);
        CourseResult pending = selectionResult(407, ResultStatus.PENDING);
        var savedRows = List.of(
                new ResultRow(101, "TEST/101", "Published student", 1, published, true, "Eligible"),
                new ResultRow(102, "TEST/102", "Draft student", 1, draft, true, "Eligible"),
                new ResultRow(407, "TEST/407", "Pending student", 1, pending, null, "Awaiting eligibility"));
        var draftBefore = draft.publicationSnapshot();
        var fixture = new ResultService() {
            @Override public List<EligibilityOffering> getOfferings() {
                return List.of(new EligibilityOffering(3, "ICT2122", "Selection fixture", "A"));
            }
            @Override public List<ResultRow> getBatchResults(int offeringId) {
                check(offeringId == 3, "saved rows load for selected offering");
                return savedRows;
            }
            @Override public boolean canPublishResult(int offeringId) { return offeringId == 3; }
            @Override public CourseResult calculateResult(int enrollmentId) { throw new AssertionError("selection must not calculate"); }
            @Override public CourseResult calculateAndSaveResult(int enrollmentId) { throw new AssertionError("selection must not save"); }
            @Override public CourseResult publishResult(int offeringId, CourseResult.PublicationSnapshot reviewed) {
                throw new AssertionError("blocked or cancelled selection must not publish");
            }
        };
        int[] confirmationCount = {0};
        onFx(() -> {
            var loader = new FXMLLoader(ResultsViewSmokeTest.class.getResource("/fxml/results.fxml"));
            loader.setControllerFactory(type -> new ResultsController(fixture) {
                @Override boolean confirmPublication(ResultRow row, CourseResult.PublicationSnapshot reviewed) {
                    check(row.enrollmentId() == 102 && reviewed.equals(draft.publicationSnapshot()),
                            "confirmation targets the reviewed saved draft");
                    confirmationCount[0]++;
                    return false;
                }
            });
            root = loader.load();
            new Scene(root, 1160, 780);
            root.applyCss(); root.layout();
            return null;
        });
        await(() -> !combo("offeringCombo").isDisabled());
        onFx(() -> { combo("offeringCombo").getSelectionModel().selectFirst(); return null; });
        await(() -> !button("refreshButton").isDisabled());
        onFx(() -> {
            check(button("publishButton").isVisible(), "assigned lecturer sees publication action");
            check(button("publishButton").isDisabled(), "no selection cannot publish");
            for (int index : new int[]{0, 1, 2, 1, 0, 2, 1}) {
                table().getSelectionModel().select(index);
                check(combo("enrollmentCombo").getValue() == savedRows.get(index), "table selects matching student");
                check(button("publishButton").isDisabled(), "published, unaudited and pending rows cannot publish");
            }
            check(label("resultStateLabel").getText().contains("Saved draft"), "102 reloads saved draft without calculation");
            check(label("resultStatusLabel").getText().equals("PASS"), "102 displays PASS");
            check(label("eligibilityLabel").getText().contains("Eligible"), "102 displays saved eligibility");
            check(!button("calculateButton").isDisabled(), "unaudited draft can be recalculated");
            check(button("saveButton").isDisabled(), "saving still requires calculation");
            check(label("publicationPolicyLabel").getText().contains("calculating lecturer"),
                    "unaudited PASS draft explains why Publish Result is disabled");
            check(label("publicationPolicyLabel").getText().contains("Save / Update Result"),
                    "unaudited draft explains how to recover");
            button("publishButton").fire();
            check(confirmationCount[0] == 0, "blocked draft does not open confirmation");
            combo("enrollmentCombo").getSelectionModel().selectFirst();
            check(table().getSelectionModel().getSelectedItem() == savedRows.getFirst(), "student selector updates table");
            check(button("calculateButton").isDisabled(), "published result cannot be recalculated");
            check(label("publicationPolicyLabel").getText().contains("already published"), "published block explained");
            combo("enrollmentCombo").getSelectionModel().selectLast();
            check(table().getSelectionModel().getSelectedItem() == savedRows.getLast(), "student selector loads pending row");
            check(label("publicationPolicyLabel").getText().contains("PASS or FAIL"), "pending block explained");
            button("refreshButton").fire();
            return null;
        });
        await(() -> !button("refreshButton").isDisabled());
        onFx(() -> {
            check(combo("enrollmentCombo").getValue() == null, "refresh clears selected student");
            check(table().getSelectionModel().getSelectedItem() == null, "refresh clears table selection");
            check(button("publishButton").isDisabled(), "refresh requires reviewing a selection");
            combo("enrollmentCombo").getSelectionModel().select(1);
            check(draftBefore.equals(draft.publicationSnapshot()), "selection and refresh preserve saved audit fields");
            check(button("publishButton").isDisabled(), "refresh cannot repair an unaudited draft");
            // Simulate loading a fully audited saved draft, without writing to any database.
            draft.setCalculatedBy(12);
            button("refreshButton").fire();
            return null;
        });
        await(() -> !button("refreshButton").isDisabled());
        onFx(() -> {
            table().getSelectionModel().select(1);
            check(!button("publishButton").isDisabled(), "audited saved PASS draft can request publication");
            button("publishButton").fire();
            check(confirmationCount[0] == 1 && !draft.isPublished(), "cancel leaves audited draft unpublished");
            button("clearButton").fire();
            check(combo("enrollmentCombo").getValue() == null && table().getItems().isEmpty(), "clear removes selection and rows");
            check(button("publishButton").isDisabled(), "clear disables publication");
            return null;
        });
    }

    private static CourseResult selectionResult(int enrollment, ResultStatus status) {
        CourseResult result = new CourseResult();
        result.setEnrollmentId(enrollment);
        result.setResultId(enrollment);
        result.setResultStatus(status);
        result.setCalculatedBy(12);
        result.setCalculatedAt(java.time.LocalDateTime.of(2026, 10, 19, 12, 0));
        if (status == ResultStatus.PASS) {
            result.setOverallMark(new BigDecimal("66.90"));
            result.setGradeRuleId(4);
            result.setGradeLetter("B+");
            result.setGradePoint(new BigDecimal("3.30"));
        }
        return result;
    }

    private static TableView<?> table() { return (TableView<?>) root.lookup("#batchTable"); }

    private static void checkGpaStaleSession() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var gpa=new lk.ac.ruhuna.ftms.service.GPAService(){
            @Override public StudentSelection getStudentSelection(){
                check(!Platform.isFxApplicationThread(),"GPA data loads off the FX thread");
                entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("GPA read timeout"); }
                catch(InterruptedException error){throw new RuntimeException(error);}
                return new StudentSelection(List.of(new lk.ac.ruhuna.ftms.dao.SemesterResultDAO.StudentOption(
                        3,"PRIVATE/3","Private Student")),1,0,List.of(),"Fixture selection");
            }
        };
        onFx(()->{
            var loader=new FXMLLoader(ResultsViewSmokeTest.class.getResource("/fxml/gpa.fxml"));
            loader.setControllerFactory(type->new GPAController(gpa));
            root=loader.load();new Scene(root,1160,780);root.applyCss();root.layout();return null;
        });
        check(entered.await(5,TimeUnit.SECONDS),"GPA background read started");
        SessionManager.logout();release.countDown();
        await(()->!button("refreshButton").isDisabled());
        onFx(()->{
            check(combo("studentCombo").getItems().isEmpty(),"late GPA response discarded after logout");
            check(label("sgpaLabel").getText().equals("Pending"),"stale GPA display cleared");return null;
        });
    }

    private static Button button(String id) { return (Button) root.lookup("#" + id); }
    private static Label label(String id) { return (Label) root.lookup("#" + id); }
    private static ComboBox<?> combo(String id) { return (ComboBox<?>) root.lookup("#" + id); }
    private static void check(boolean condition, String label) { if (!condition) { throw new AssertionError(label); } }
    private static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!onFx(condition)) {
            if (System.nanoTime() > deadline) { throw new AssertionError("UI operation timed out"); }
            Thread.sleep(20);
        }
    }

    private static final class InMemoryService extends ResultService {
        private CourseResult saved;
        private boolean authorized;
        private int publications;
        private boolean waitForLoad;
        private final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        @Override public boolean canPublishResult(int offeringId) {
            check(!Platform.isFxApplicationThread(),"publication authority loads off the FX thread");
            return authorized && offeringId == 1;
        }
        @Override public List<EligibilityOffering> getOfferings() {
            return List.of(new EligibilityOffering(1, "TEST", "Test offering", "A"));
        }
        @Override public List<ResultRow> getBatchResults(int offeringId) {
            check(!Platform.isFxApplicationThread(),"result rows load off the FX thread");
            if(waitForLoad){
                entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Result read timeout"); }
                catch(InterruptedException error){throw new RuntimeException(error);}
            }
            return List.of(new ResultRow(1, "TEST/1", "Test Student", 1, saved));
        }
        @Override public CourseResult calculateResult(int enrollmentId) {
            CourseResult result = new CourseResult();
            result.setEnrollmentId(enrollmentId);
            result.setCaMark(new BigDecimal("72.50"));
            result.setFinalExamMark(new BigDecimal("60.00"));
            result.setOverallMark(new BigDecimal("65.00"));
            result.setResultStatus(ResultStatus.PASS);
            result.setGradeLetter("TEST");
            result.setGradeRuleId(90);
            result.setGradePoint(new BigDecimal("3.25"));
            return result;
        }
        @Override public CourseResult calculateAndSaveResult(int enrollmentId) {
            saved = calculateResult(enrollmentId);
            saved.setResultId(1);
            saved.setCalculatedAt(java.time.LocalDateTime.of(2026, 10, 19, 12, 0));
            saved.setCalculatedBy(12);
            return saved;
        }
        @Override public CourseResult publishResult(int offeringId, CourseResult.PublicationSnapshot reviewed) {
            check(authorized && offeringId == 1, "controller supplies the selected offering");
            check(saved.publicationSnapshot().equals(reviewed), "controller supplies reviewed snapshot");
            publications++;
            saved.setPublished(true);
            saved.setPublishedAt(java.time.LocalDateTime.now());
            return saved;
        }
    }
}
