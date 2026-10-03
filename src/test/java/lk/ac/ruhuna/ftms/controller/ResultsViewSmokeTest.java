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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Loads the real FXML with an in-memory service; no database writes or visible window. */
public final class ResultsViewSmokeTest {
    private static Parent root;

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) { throw new AssertionError("JavaFX startup timed out"); }
        try {
            onFx(() -> {
                FXMLLoader loader = new FXMLLoader(ResultsViewSmokeTest.class.getResource("/fxml/results.fxml"));
                loader.setControllerFactory(type -> new ResultsController(new InMemoryService()));
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
                check(((TableView<?>) root.lookup("#batchTable")).getItems().size() == 1, "batch row retained");
                button("refreshButton").fire();
                return null;
            });
            await(() -> !button("refreshButton").isDisabled());
            onFx(() -> {
                combo("enrollmentCombo").getSelectionModel().selectFirst();
                check(label("resultStateLabel").getText().contains("Saved draft"), "refresh reloads persisted result");
                button("clearButton").fire();
                check(combo("enrollmentCombo").getItems().isEmpty(), "clear empties batch");
                check(button("saveButton").isDisabled(), "clear disables saving");
                return null;
            });
            System.out.println("Results FXML and controller smoke checks passed.");
        } finally { Platform.exit(); }
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
            result.setGradePoint(new BigDecimal("3.25"));
            return result;
        }
        @Override public CourseResult calculateAndSaveResult(int enrollmentId, Integer calculatedBy) {
            saved = calculateResult(enrollmentId);
            saved.setResultId(1);
            return saved;
        }
    }
}
