package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.service.StudentCourseService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** Opt-in JavaFX smoke against live SELECTs only. Does not enroll, update metadata or open a save dialog. */
public final class EnrollmentMaterialsViewSmokeTest {
    private static Scene scene;
    private static Stage stage;
    private static int checks;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var users = new UserDAO().getAllUsers();
        var admin = users.stream().filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow();
        var student = users.stream().filter(u -> u.getUsername().equals("stu")).findFirst().orElseThrow();
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        if (!ready.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                stage = new Stage(); scene = new Scene(new BorderPane(), 1200, 850); stage.setScene(scene);
                SessionManager.startSession(admin);
                navigate("admin_dashboard.fxml");
                button("Manage Student Enrollments").fire(); return null;
            });
            loaded();
            fx(() -> {
                check(scene.getRoot().getId().equals("adminEnrollmentsRoot"), "Admin shortcut opens enrollment screen");
                check(!table("enrollmentTable").getItems().isEmpty(), "Admin enrollment rows load");
                ((TextField) scene.lookup("#studentSearch")).setText("stu"); button("Search Students").fire(); return null;
            });
            loaded();
            fx(() -> {
                check(!((ComboBox<?>) scene.lookup("#studentBox")).getItems().isEmpty(), "Student search returns candidates");
                ((TextField) scene.lookup("#offeringSearch")).setText("ICT2122"); button("Search Offerings").fire(); return null;
            });
            loaded();
            fx(() -> {
                var offerings = (ComboBox<?>) scene.lookup("#offeringBox");
                check(!offerings.getItems().isEmpty(), "Offering search returns matching course");
                offerings.getSelectionModel().selectFirst();
                check(((Label) scene.lookup("#offeringDetails")).getText().contains("ICT2122"), "Offering details include course code");
                snapshot("admin-enrollments.png");
                button("Back to Admin Dashboard").fire();
                check(SessionManager.getCurrentUser().getUserId() == admin.getUserId(), "Admin back preserves session");
                SessionManager.startSession(student); navigate("student_courses.fxml"); return null;
            });
            loaded();
            var courses = new StudentCourseService().getMyCourses();
            for (var course : courses) {
                fx(() -> {
                    var table = (TableView<List<String>>) scene.lookup("#recordsTable");
                    check(table.getColumns().size() == 6, "Original course columns preserved");
                    check(button("View Course Materials").isDisabled(), "Materials disabled without selection");
                    var sort = table.getColumns().getFirst(); sort.setSortType(TableColumn.SortType.DESCENDING);
                    table.getSortOrder().setAll(sort); table.sort();
                    var row = table.getItems().stream().filter(r -> r.getFirst().equals(course.courseCode())).findFirst().orElseThrow();
                    table.getSelectionModel().select(row);
                    check(!button("View Course Materials").isDisabled(), "Selection enables materials");
                    button("View Course Materials").fire(); return null;
                });
                loaded();
                fx(() -> {
                    check(((Label) scene.lookup("#courseCodeLabel")).getText().equals(course.courseCode()), "Sorted row keeps its course identity");
                    check(!table("materialsTable").getItems().isEmpty(), "Authorized course metadata loads");
                    check(!table("materialsTable").isEditable(), "Student material table is read-only");
                    check(scene.getWindow() == stage, "Navigation remains in the same Stage");
                    snapshot("student-materials-" + course.courseCode() + ".png");
                    button("Back to My Courses").fire(); return null;
                });
                loaded();
                fx(() -> { button("Refresh").fire(); return null; });
                loaded();
            }
            fx(() -> {
                snapshot("student-courses.png");
                button("Back to Student Dashboard").fire();
                check(SessionManager.getCurrentUser().getUserId() == student.getUserId(), "Student back preserves session");
                navigate("student_courses.fxml"); return null;
            });
            loaded();
            fx(() -> {
                button("Logout").fire();
                check(!SessionManager.isLoggedIn(), "Logout clears session");
                check(scene.getWindow() == stage, "Logout keeps the Stage");
                return null;
            });
            System.out.println("Enrollment/material JavaFX smoke checks passed: " + checks);
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; }); Platform.exit();
        }
    }

    private static void navigate(String resource) throws Exception {
        NavigationUtil.navigate(scene.getRoot(), "/fxml/" + resource, "FTMS UI verification");
        scene.getRoot().applyCss(); scene.getRoot().layout();
    }
    private static TableView<?> table(String id) { return (TableView<?>) scene.lookup("#" + id); }
    private static Button button(String text) {
        return scene.getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(text))
                .map(n -> (Button) n).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
    private static void loaded() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                var progress = scene.lookup("#progressIndicator");
                scene.getRoot().applyCss(); scene.getRoot().layout();
                return progress == null || !progress.isVisible();
            })) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Screen failed to finish loading");
    }
    private static void snapshot(String name) throws Exception {
        Parent root = scene.getRoot(); root.applyCss(); root.layout();
        var image = root.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        Path directory = Path.of("target", "enrollment-materials-ui"); Files.createDirectories(directory);
        ImageIO.write(output, "png", directory.resolve(name).toFile());
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
