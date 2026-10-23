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
            System.out.println("Results FXML and controller smoke checks passed.");
        } finally { Platform.exit(); SessionManager.logout(); }
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
        @Override public boolean canPublishResult(int offeringId) { return authorized && offeringId == 1; }
        @Override public List<EligibilityOffering> getOfferings() {
            return List.of(new EligibilityOffering(1, "TEST", "Test offering", "A"));
        }
        @Override public List<ResultRow> getBatchResults(int offeringId) {
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
