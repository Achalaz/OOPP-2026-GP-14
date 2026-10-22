package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.service.StudentDatabaseFixture;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.List;
import java.util.concurrent.*;

/** Real JavaFX controls and MySQL temporary tables; no visible window or production writes. */
public final class StudentPortalViewSmokeTest {
    private static Scene scene;
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var db = new StudentDatabaseFixture()) {
            db.publishSamples();
            SessionManager.logout();
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            check(started.await(10,TimeUnit.SECONDS),"JavaFX started");
            try {
                fx(() -> {
                    scene = new Scene(FXMLLoader.load(StudentPortalViewSmokeTest.class.getResource("/fxml/login.fxml")),1100,720);
                    layout();
                    ((TextField)node("usernameField")).setText("academic-test-3");
                    ((PasswordField)node("passwordField")).setText(db.password);
                    button("loginButton").fire();
                    return null;
                });
                await(() -> "studentRoot".equals(scene.getRoot().getId()));
                var identity = SessionManager.requireSession();
                check(identity.userId()==3,"real password login selects student account");
                fx(() -> { snapshot("student-dashboard-preserved"); return null; });
                for (String entry : List.of("My Profile","View Profile")) {
                    click(entry); ready();
                    fx(() -> {
                        check("profileRoot".equals(scene.getRoot().getId()),entry+" opens existing profile");
                        check(!((TextField)node("fullNameField")).isEditable(),"name locked");
                        check(!((TextField)node("usernameField")).isEditable(),"username locked");
                        check(!((TextField)node("roleField")).isEditable(),"role locked");
                        check(((TextField)node("phoneField")).isEditable(),"phone editable");
                        check(node("passwordField")==null,"no password workflow");
                        button("dashboardButton").fire(); return null;
                    });
                    check(SessionManager.isCurrentSession(identity),"profile preserves login");
                }
                for (String entry : List.of("My Attendance","View Attendance","My Medical","View Medical",
                        "My Courses","View Courses","My Published Results","View Results","My SGPA / CGPA","View SGPA / CGPA")) {
                    click(entry); ready();
                    fx(() -> {
                        String feature=scene.getRoot().getUserData().toString();
                        check(!table("recordsTable").isEditable(),entry+" is read only");
                        check(node("saveButton")==null && node("studentCombo")==null,"no write controls or student selector");
                        switch (feature) {
                            case "attendance" -> {
                                check(table("recordsTable").getItems().size()==3,"own sessions only");
                                check(table("summaryTable").getItems().size()==2,"own attendance summaries");
                            }
                            case "medical" -> {
                                check(table("recordsTable").getItems().size()==3,"own medical records only");
                                String text=table("recordsTable").getItems().toString();
                                check(text.contains("PENDING")&&text.contains("APPROVED")&&text.contains("REJECTED"),"stored medical statuses");
                            }
                            case "courses" -> check(table("recordsTable").getItems().size()==2,"own enrollments only");
                            case "results" -> {
                                check(table("recordsTable").getItems().size()==1,"only own published result; draft omitted");
                                check(table("summaryTable").getItems().size()==1,"draft-only semester omitted");
                                check(table("summaryTable").getItems().toString().contains("4.00"),"existing GPA calculation displayed");
                                snapshot("student-published-results");
                            }
                            default -> throw new AssertionError(feature);
                        }
                        button("dashboardButton").fire();
                        check("studentRoot".equals(scene.getRoot().getId()),entry+" returns to dashboard");
                        return null;
                    });
                    check(SessionManager.isCurrentSession(identity),entry+" preserves login");
                }
                for (String entry : List.of("Notices","View Notices")) {
                    click(entry);
                    fx(() -> {
                        check(table("noticeTable").getItems().size()==2,"only active ALL/STUDENTS notices");
                        check(!((TextArea)node("contentArea")).isEditable(),"notice content read only");
                        buttonText("Back to Dashboard").fire(); return null;
                    });
                }
                db.academic.execute("UPDATE course_results SET published=FALSE WHERE enrollment_id=100");
                click("View Results"); ready();
                fx(() -> {
                    check(table("recordsTable").getItems().isEmpty(),"no draft rows in results");
                    check(((Label)node("statusLabel")).getText().equals("No published results available."),"exact empty message");
                    button("logoutButton").fire();
                    check("loginRoot".equals(scene.getRoot().getId()),"viewer logout opens login");
                    check(!SessionManager.isLoggedIn(),"viewer logout ends session");
                    return null;
                });
                db.academic.login(3,"STUDENT");
                fx(() -> {
                    scene.setRoot(FXMLLoader.load(StudentPortalViewSmokeTest.class.getResource("/fxml/student_dashboard.fxml")));
                    layout();button("logoutButton").fire();
                    check(!SessionManager.isLoggedIn() && "loginRoot".equals(scene.getRoot().getId()),"dashboard logout works");
                    return null;
                });
                System.out.println("Student JavaFX/login/navigation checks passed: "+checks);
            } finally { Platform.exit(); }
        }
    }

    private static void click(String text) throws Exception { fx(() -> {layout();buttonText(text).fire();return null;}); }
    private static void ready() throws Exception {
        await(() -> node("refreshButton") instanceof Button b && !b.isDisabled());
        fx(() -> {
            String text=((Label)node("statusLabel")).getText();
            check(!text.startsWith("Unable")&&!text.startsWith("Cannot"),"load succeeded: "+text);return null;
        });
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        for(int i=0;i<400;i++) { if(fx(condition))return; Thread.sleep(25); }
        throw new AssertionError("Timed out waiting for student view");
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);
    }
    private static void layout() { scene.getRoot().applyCss();scene.getRoot().layout(); }
    private static javafx.scene.Node node(String id) { layout();return scene.getRoot().lookup("#"+id); }
    private static Button button(String id) { return (Button)node(id); }
    private static TableView<?> table(String id) { return (TableView<?>)node(id); }
    private static Button buttonText(String text) {
        return scene.getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && text.equals(b.getText()))
                .map(n -> (Button)n).findFirst().orElseThrow(() -> new AssertionError("Missing button: "+text));
    }
    private static void snapshot(String name) throws Exception {
        layout(); var image=scene.getRoot().snapshot(null,null);
        var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(output,"png",new java.io.File("target/"+name+".png"));
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message);checks++; }
}
