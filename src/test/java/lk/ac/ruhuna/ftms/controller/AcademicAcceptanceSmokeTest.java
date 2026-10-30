package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO;
import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.util.PasswordUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Real credential login, controls, dialogs and JDBC; every write targets connection-local tables.
 * The explicitly confirmed eligibility and grade boundaries are test fixtures, not academic policy. */
public final class AcademicAcceptanceSmokeTest {
    private static Scene scene;
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var db = new AcademicDatabaseFixture()) {
            String password = UUID.randomUUID().toString();
            String hash = PasswordUtil.hashPassword(password.toCharArray());
            db.execute("UPDATE users SET password_hash='" + hash + "' WHERE user_id IN (1,3,4)");
            SessionManager.logout();
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
            check(started.await(10, TimeUnit.SECONDS), "JavaFX started");
            try {
                fx(() -> { scene = new Scene(FXMLLoader.load(AcademicAcceptanceSmokeTest.class.getResource("/fxml/login.fxml")),1280,900); return null; });
                login(1, password, "lecturerRoot");
                var lecturer = SessionManager.requireSession();
                click("assessmentsCard"); ready();
                select("offeringCombo", o -> ((EligibilityOffering)o).offeringId() == 10); ready();
                fx(() -> { field("nameField").setText("Acceptance practice"); field("maxField").setText("20"); field("weightField").setText("0"); return null; });
                click("saveButton"); ready();
                check(new AssessmentService().getAssessments(10).size() == 3, "assessment CREATE persisted");
                fx(() -> { selectAssessment("Acceptance practice"); field("nameField").setText("Updated practice"); return null; });
                click("saveButton"); ready();
                check(new AssessmentService().getAssessments(10).stream().anyMatch(a -> a.getAssessmentName().equals("Updated practice")), "assessment UPDATE persisted");
                fx(() -> { selectAssessment("Updated practice"); dialog("deleteButton", ButtonType.OK, "Delete the unreferenced assessment"); return null; }); ready();
                check(new AssessmentService().getAssessments(10).size() == 2, "assessment DELETE persisted");
                fx(() -> {
                    selectAssessment("CA");
                    ((TextArea)scene.getRoot().lookup("#descriptionField")).setText("Read chapter 1 before the quiz.");
                    ((DatePicker)scene.getRoot().lookup("#dateField")).setValue(java.time.LocalDate.of(2026,11,1));
                    return null;
                });
                click("saveButton"); ready();
                fx(() -> { selectAssessment("CA"); dialog("releaseButton", ButtonType.CANCEL, "Marks and final results are published separately"); return null; });
                check(!new AssessmentService().getAssessments(10).getFirst().isPublished(), "cancelled assessment release remains Draft");
                fx(() -> { dialog("releaseButton", ButtonType.OK, "cannot be edited or deleted"); return null; }); ready();
                var releasedAssessment = new AssessmentService().getAssessments(10).getFirst();
                check(releasedAssessment.isPublished() && releasedAssessment.getPublishedAt()!=null, "assessment release persists flag and timestamp");
                fx(() -> {
                    selectAssessment("CA");
                    check(button("saveButton").isDisabled() && button("deleteButton").isDisabled() && button("releaseButton").isDisabled(), "released assessment write actions disabled");
                    check(field("nameField").isDisabled(), "released details protected");
                    check(table("assessmentTable").getColumns().stream().anyMatch(c -> "Status".equals(c.getText())), "publication status column loaded");
                    return null;
                });
                textClick("Marks Entry");
                select("offeringCombo", o -> ((EligibilityOffering)o).offeringId() == 10);
                saveMark(10, "40"); saveMark(11, "80");
                check(new MarkService().getAllMarks().size() == 2, "both marks persisted from UI");
                textClick("Eligibility"); ready();
                select("offeringCombo", o -> ((EligibilityOffering)o).offeringId() == 10); ready();
                select("enrollmentCombo", o -> ((EligibilityRow)o).enrollmentId() == 100);
                click("calculateButton"); ready();
                fx(() -> { check(label("caPercentageLabel").getText().equals("80.00%"), "CA displayed"); check(label("overallEligibilityLabel").getText().equals("Pending"), "unknown policy stays pending"); return null; });
                click("saveButton"); ready();
                check(new EligibilityDAO().getByEnrollmentId(100).isOverallEligible() == null, "pending eligibility saved as NULL");
                dashboard(lecturer, "lecturerRoot"); click("resultsCard"); ready();
                selectResult(100);
                click("calculateButton"); ready(); click("saveButton"); ready();
                var pending = new CourseResultDAO().getByEnrollmentId(100);
                check(!pending.isPublished(), "pending draft stays unpublished");
                fx(() -> { check(button("publishButton").isDisabled(), "UI blocks unresolved result publication"); return null; });

                // A controlled, pre-approved decision is required to test the successful publication branch.
                // This helper writes only the fixture eligibility table; publication itself is always through UI/service.
                db.confirmedFixtureEligibility(100);
                click("calculateButton"); ready(); click("saveButton"); ready();
                var draft = new CourseResultDAO().getByEnrollmentId(100);
                check(!draft.isPublished() && draft.getOverallMark().doubleValue() == 80, "Save Draft stores calculated unpublished result");
                fx(() -> { dialog("publishButton", ButtonType.CANCEL, "Enrollment 100"); return null; });
                check(!new CourseResultDAO().getByEnrollmentId(100).isPublished(), "publication CANCEL preserves draft");
                fx(() -> { dialog("publishButton", ButtonType.OK, "Enrollment 100"); return null; }); ready();
                var published = new CourseResultDAO().getByEnrollmentId(100);
                check(published.isPublished() && published.getPublishedAt() != null, "UI publication persists timestamp and published flag");
                fx(() -> { check(button("publishButton").isDisabled(), "duplicate publication disabled"); return null; });
                textClick("SGPA / CGPA"); ready();
                select("studentCombo", o -> o.toString().contains("TEST/3")); ready();
                select("semesterCombo", o -> ((lk.ac.ruhuna.ftms.dao.SemesterResultDAO.SemesterOption)o).semesterId() == 9); ready();
                click("calculateButton"); ready();
                fx(() -> { check(label("sgpaLabel").getText().equals("4.00") && label("cgpaLabel").getText().equals("4.00"), "published result flows into GPA UI"); return null; });
                click("saveButton"); ready();
                check(new SemesterResultDAO().getByStudentAndSemester(3,9).getSgpa() == 4.0, "GPA save persisted recomputed value");
                textClick("Reports"); ready();
                select("studentCombo", o -> o.toString().contains("TEST/3")); click("studentReportButton"); ready();
                fx(() -> { check(table("reportTable").getItems().size() == 2, "individual report includes published and pending enrollment"); check(label("passLabel").getText().equals("1"), "individual pass count"); return null; });
                select("batchCombo", o -> o.equals(2024)); click("batchReportButton"); ready();
                fx(() -> { check(table("reportTable").getItems().size() == 2, "batch report loaded"); check(label("averageLabel").getText().equals("80.00"), "report average excludes pending marks"); return null; });
                dashboard(lecturer, "lecturerRoot");

                // Another student's valid publication is created through the guarded service, never SQL.
                var marks = new MarkService();
                marks.saveMark(new Mark(101,10,40.0,1)); marks.saveMark(new Mark(101,11,60.0,1));
                db.execute("UPDATE attendance_records SET attendance_status='PRESENT' WHERE enrollment_id=101");
                db.confirmedFixtureEligibility(101);
                var results = new ResultService();
                results.publishResult(10, results.calculateAndSaveResult(101).publicationSnapshot());
                results.calculateAndSaveResult(200); // own unpublished row must stay hidden
                new AssessmentService().release(20,20); // metadata release must leave that result unpublished
                check(!new CourseResultDAO().getByEnrollmentId(200).isPublished(), "assessment release preserves draft result");
                logout();
                login(3, password, "studentRoot");
                var student = SessionManager.requireSession();
                for (String entry : List.of("My Assessments", "View Released Assessments")) {
                    textClick(entry); ready();
                    fx(() -> {
                        check(table("recordsTable").getItems().size()==2, "only released assessments in own offerings visible");
                        check(!table("recordsTable").isEditable(), "assessment viewer read only");
                        check(scene.getRoot().lookup("#saveButton")==null && scene.getRoot().lookup("#releaseButton")==null && scene.getRoot().lookup("#deleteButton")==null, "student has no assessment management controls");
                        check(table("recordsTable").getColumns().size()==11, "all assessment metadata columns loaded");
                        table("recordsTable").getSelectionModel().selectFirst();
                        var description=(TextArea)scene.getRoot().lookup("#assessmentDescription");
                        check(!description.isEditable() && description.getText().equals("Read chapter 1 before the quiz."), "full description displayed read only");
                        check(table("recordsTable").getItems().getFirst().toString().contains("2026-11-01"), "assessment date visible");
                        return null;
                    });
                    dashboard(student, "studentRoot");
                }
                for (String entry : List.of("My Grades and GPA", "View Results")) {
                    textClick(entry); ready();
                    fx(() -> {
                        check(table("recordsTable").getItems().size() == 1, "only own published course visible");
                        check(((List<?>)table("recordsTable").getItems().getFirst()).get(3).equals("A"), "own grade shown, other student's B hidden");
                        check(table("summaryTable").getItems().size() == 1 && table("summaryTable").getItems().toString().contains("4.00"), "only published semester GPA visible");
                        check(!table("recordsTable").isEditable() && scene.getRoot().lookup("#studentCombo") == null && scene.getRoot().lookup("#saveButton") == null, "student viewer is self-only and read only");
                        return null;
                    });
                    dashboard(student, "studentRoot");
                }
                var retained = new StudentAccessService(student);
                logout();
                login(4, password, "studentRoot");
                textClick("My Assessments"); ready();
                fx(() -> { check(table("recordsTable").getItems().size()==1, "second student cannot see released assessment from another offering"); return null; });
                dashboard(SessionManager.requireSession(), "studentRoot");
                try { retained.requireSession(); throw new AssertionError("Old student session accepted"); }
                catch (SecurityException expected) { checks++; }
                textClick("View Results"); ready();
                var secondStudentResults = new ResultService().getMyPublishedResults(SessionManager.requireSession());
                check(secondStudentResults.size() == 1 && secondStudentResults.getFirst().grade().equals("B"), "second login has its own publication only");
                logout();
                System.out.println("Academic credential-login/UI/MySQL acceptance checks passed: " + checks + "; temporary fixtures only.");
            } finally { fx(() -> { for (Window w : List.copyOf(Window.getWindows())) w.hide(); return null; }); Platform.exit(); }
        }
    }

    private static void saveMark(int assessment, String value) throws Exception {
        fx(() -> {
            combo("enrollmentCombo").getSelectionModel().clearSelection();
            combo("enrollmentCombo").getSelectionModel().selectFirst();
            combo("assessmentCombo").getSelectionModel().clearSelection();
            selectNow("assessmentCombo", o -> ((Assessment)o).getAssessmentId() == assessment);
            field("markField").setText(value);
            dialog("saveButton", ButtonType.OK, "Mark saved successfully."); return null;
        });
    }
    private static void selectResult(int enrollment) throws Exception {
        select("offeringCombo", o -> ((EligibilityOffering)o).offeringId() == 10); ready();
        select("enrollmentCombo", o -> ((ResultRow)o).enrollmentId() == enrollment);
    }
    private static void selectAssessment(String name) {
        TableView<?> table = table("assessmentTable");
        for (int i=0; i<table.getItems().size(); i++) if (((Assessment)table.getItems().get(i)).getAssessmentName().equals(name)) { table.getSelectionModel().select(i); return; }
        throw new AssertionError("Missing assessment " + name);
    }
    private static void login(int id, String password, String root) throws Exception {
        fx(() -> { field("usernameField").setText("academic-test-" + id); field("passwordField").setText(password); button("loginButton").fire(); return null; });
        await(() -> root.equals(scene.getRoot().getId()));
        check(SessionManager.requireSession().userId() == id, "real password authentication chooses account " + id);
    }
    private static void logout() throws Exception {
        textClick("lecturerRoot".equals(fx(() -> scene.getRoot().getId())) ? "Log Out" : "Logout");
        fx(() -> { check(!SessionManager.isLoggedIn() && "loginRoot".equals(scene.getRoot().getId()), "Logout clears session and opens login"); return null; });
    }
    private static void dashboard(SessionManager.SessionIdentity identity, String root) throws Exception {
        click("dashboardButton");
        fx(() -> { check(root.equals(scene.getRoot().getId()) && SessionManager.isCurrentSession(identity), "Back to Dashboard retains exact login"); return null; });
    }
    private static void dialog(String button, ButtonType response, String expected) throws Exception {
        CompletableFuture<String> shown = new CompletableFuture<>();
        Platform.runLater(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane) {
                    shown.complete(pane.getContentText());
                    Button target = (Button)pane.lookupButton(response);
                    if (target != null) target.fire(); else window.hide();
                    return;
                }
            }
            shown.completeExceptionally(new AssertionError("Expected dialog missing"));
        });
        check(!button(button).isDisabled(), button + " enabled");
        button(button).fire();
        check(shown.get(5,TimeUnit.SECONDS).contains(expected), "expected dialog content");
    }
    private static void select(String id, Predicate<Object> match) throws Exception { fx(() -> { selectNow(id,match); return null; }); }
    private static void selectNow(String id, Predicate<Object> match) {
        var box=combo(id);
        for(int i=0;i<box.getItems().size();i++) if(match.test(box.getItems().get(i))) { box.getSelectionModel().select(i); return; }
        throw new AssertionError("No matching option in " + id);
    }
    private static void click(String id) throws Exception { fx(() -> { check(!button(id).isDisabled(),id + " enabled"); button(id).fire(); return null; }); }
    private static void textClick(String text) throws Exception {
        fx(() -> { layout(); scene.getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && text.equals(b.getText())).map(n -> (Button)n).findFirst().orElseThrow().fire(); return null; });
    }
    private static void ready() throws Exception { await(() -> !button("refreshButton").isDisabled()); }
    private static void await(Callable<Boolean> condition) throws Exception {
        for(int i=0;i<400;i++) { if(fx(condition))return; Thread.sleep(25); }
        throw new AssertionError("Timed out waiting for academic UI");
    }
    private static <T>T fx(Callable<T> work) throws Exception { var task=new FutureTask<>(work); Platform.runLater(task); return task.get(20,TimeUnit.SECONDS); }
    private static void layout(){scene.getRoot().applyCss();scene.getRoot().layout();}
    private static Button button(String id){layout();return (Button)scene.getRoot().lookup("#"+id);}
    private static TextField field(String id){return (TextField)scene.getRoot().lookup("#"+id);}
    private static ComboBox<?> combo(String id){return (ComboBox<?>)scene.getRoot().lookup("#"+id);}
    private static Label label(String id){return (Label)scene.getRoot().lookup("#"+id);}
    private static TableView<?> table(String id){return (TableView<?>)scene.getRoot().lookup("#"+id);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
