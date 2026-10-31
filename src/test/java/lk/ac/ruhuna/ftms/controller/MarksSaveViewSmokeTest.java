package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO;
import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.service.AcademicDatabaseFixture;
import lk.ac.ruhuna.ftms.service.MarkService;
import lk.ac.ruhuna.ftms.service.ResultService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Real FXML and services; all SQL writes use connection-local temporary fixture tables. */
public final class MarksSaveViewSmokeTest {
    private record Feedback(Alert.AlertType type, String title, String message) {}
    private static final List<Feedback> feedback = new ArrayList<>();
    private static Scene scene;
    private static int checks;

    public static void main(String[] args) throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX started");
        try (var db = new AcademicDatabaseFixture()) {
            db.login(2, "LECTURER");
            new MarkService().saveMark(new Mark(300, 30, 50.0, 1));
            db.login(1, "LECTURER");
            var marks = new MarkService();
            marks.saveMark(new Mark(100, 10, 40.0, 1));
            marks.saveMark(new Mark(100, 11, 80.0, 1));
            marks.saveMark(new Mark(200, 20, 60.0, 1));
            db.confirmedFixtureEligibility(100);
            db.confirmedFixtureEligibility(200);
            var results = new ResultService();
            var toPublish = results.calculateAndSaveResult(100);
            var draft = results.calculateAndSaveResult(200);

            fx(() -> {
                var loader = new FXMLLoader(MarksSaveViewSmokeTest.class.getResource("/fxml/marks_entry.fxml"));
                loader.setControllerFactory(type -> new MarksController() {
                    @Override void showAlert(Alert.AlertType type, String title, String message) {
                        feedback.add(new Feedback(type, title, message));
                    }
                });
                scene = new Scene(loader.load(), 1160, 780);
                scene.getRoot().applyCss(); scene.getRoot().layout();
                check(feedback.isEmpty(), "marks screen loads without errors");
                check(table().getItems().stream().noneMatch(m -> m.getEnrollmentId() == 300), "unassigned marks hidden");
                select(100, 10);
                field("markField").setText("30");
                return null;
            });
            // Publish only the fixture after the form loads, exercising the authoritative save-time guard.
            var published = results.publishResult(10, toPublish.publicationSnapshot());
            var before = snapshot();
            fx(() -> {
                check(saveWithoutTrace().type() == Alert.AlertType.WARNING, "published edit produces a warning, not an unexpected error");
                var notice = feedback.getLast();
                check(notice.title().equals("Published Result"), "published warning has a clear title");
                check(notice.message().contains("already published") && notice.message().contains("No changes were saved")
                        && notice.message().contains("formal correction"), "warning explains protection and next step");
                check(field("markField").getText().equals("30"), "blocked save preserves the attempted input");
                check(table().getSelectionModel().getSelectedItem().getMarksObtained() == 40.0, "blocked save leaves displayed mark unchanged");
                return null;
            });
            check(before.equals(snapshot()), "published rejection preserves marks, audit, eligibility and results");

            fx(() -> {
                select(200, 20);
                field("markField").setText("70");
                check(saveWithoutTrace().type() == Alert.AlertType.INFORMATION, "authorized unpublished edit succeeds");
                check(feedback.getLast().message().equals("Mark saved successfully."), "draft edit reports success");
                check(field("markField").getText().isEmpty(), "successful save clears the form");
                check(table().getItems().stream().anyMatch(m -> m.getEnrollmentId() == 200 && m.getMarksObtained() == 70.0),
                        "draft mark reloaded from database");
                return null;
            });
            var changed = new CourseResultDAO().getByEnrollmentId(200);
            check(!changed.isPublished() && changed.getPublishedAt() == null, "mark edit never publishes a result");
            check(changed.getResultStatus() == CourseResult.ResultStatus.PENDING && changed.getGradePoint() == null,
                    "mark edit invalidates the saved grade");
            check(new EligibilityDAO().getByEnrollmentId(200).isOverallEligible() == null, "mark edit invalidates eligibility");
            try {
                results.publishResult(20, draft.publicationSnapshot());
                throw new AssertionError("Stale draft published after mark edit");
            } catch (lk.ac.ruhuna.ftms.exception.ResultCalculationException expected) { checks++; }

            before = snapshot();
            fx(() -> {
                field("enrollmentIdField").setText("300");
                field("assessmentIdField").setText("30");
                field("markField").setText("75");
                field("attemptNoField").setText("1");
                var notice = saveWithoutTrace();
                check(notice.type() == Alert.AlertType.ERROR && notice.title().equals("Access Denied"), "unauthorized save remains access denied");
                check(field("enrollmentIdField").getText().isEmpty() && table().getItems().isEmpty(), "denied access clears sensitive form and rows");
                return null;
            });
            check(before.equals(snapshot()), "unauthorized rejection preserves every fixture mark and academic result");
            check(published.publicationSnapshot().equals(new CourseResultDAO().getByEnrollmentId(100).publicationSnapshot()),
                    "published snapshot remains protected throughout");
            System.out.println("Marks Save JavaFX/MySQL checks passed: " + checks + "; temporary tables only.");
        } finally { Platform.exit(); }
    }

    private static Feedback saveWithoutTrace() {
        var originalError = System.err;
        var captured = new ByteArrayOutputStream();
        int previous = feedback.size();
        try (var output = new PrintStream(captured)) {
            System.setErr(output);
            ((Button) scene.getRoot().lookup("#saveButton")).fire();
        } finally { System.setErr(originalError); }
        check(feedback.size() == previous + 1, "save produces exactly one message");
        check(captured.size() == 0, "expected save outcome does not print a stack trace: " + captured);
        return feedback.getLast();
    }

    private static void select(int enrollment, int assessment) {
        table().getSelectionModel().select(table().getItems().stream()
                .filter(m -> m.getEnrollmentId() == enrollment && m.getAssessmentId() == assessment).findFirst().orElseThrow());
    }
    @SuppressWarnings("unchecked")
    private static TableView<Mark> table() { return (TableView<Mark>) scene.getRoot().lookup("#marksTable"); }
    private static TextField field(String id) { return (TextField) scene.getRoot().lookup("#" + id); }
    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static List<List<String>> snapshot() throws Exception {
        var snapshot = new ArrayList<List<String>>();
        try (var c = DBConnection.getConnection()) {
            for (String table : List.of("marks", "course_results", "eligibility_records")) {
                try (var p = c.prepareStatement("SELECT * FROM " + table + " ORDER BY 1"); var rows = p.executeQuery()) {
                    while (rows.next()) {
                        var values = new ArrayList<String>(); values.add(table);
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) values.add(rows.getString(i));
                        snapshot.add(values);
                    }
                }
            }
        }
        return snapshot;
    }
}
