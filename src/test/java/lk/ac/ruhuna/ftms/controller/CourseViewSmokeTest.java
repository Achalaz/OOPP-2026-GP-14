package lk.ac.ruhuna.ftms.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Real FXML/actions and live read-only navigation. CRUD uses an in-memory DAO, never live writes. */
public final class CourseViewSmokeTest {
    private static int checks;
    private static Scene scene;
    private static Stage stage;

    public static void main(String[] args) throws Exception {
        var admin = new UserDAO().getAllUsers().stream()
                .filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow();
        int liveCourses = new CourseDAO().findAll().size();
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX started");
        try {
            fx(() -> {
                Platform.setImplicitExit(false);
                SessionManager.logout(); deniedAccess();
                for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
                    SessionManager.startSession(new User(1, "fixture", "Fixture", "", role, true));
                    deniedAccess();
                }
                SessionManager.startSession(admin);
                scene = new Scene(FXMLLoader.load(CourseViewSmokeTest.class.getResource("/fxml/login.fxml")), 1024, 600);
                stage = new Stage(); stage.setScene(scene); stage.setOpacity(0); stage.show();
                var identity = SessionManager.requireSession();
                double width = stage.getWidth(), height = stage.getHeight();
                NavigationUtil.navigate(scene.getRoot(), NavigationUtil.dashboardResource(), "Admin Dashboard");
                check("adminRoot".equals(scene.getRoot().getId()), "authenticated admin login destination");
                for (boolean card : new boolean[]{false, true}) {
                    layout();
                    Button entry = scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance)
                            .map(Button.class::cast).filter(b -> b.getText().contains("Courses")
                                    && b.getText().contains("Manage") == card).findFirst().orElseThrow();
                    entry.fire(); layout();
                    check("coursesRoot".equals(scene.getRoot().getId()), "course entry " + (card ? "card" : "sidebar"));
                    check(table().getItems().size() == liveCourses, "navigation injects service and loads MySQL");
                    check(stage.getScene() == scene, "course screen retains scene and stage");
                    check(Math.abs(width - stage.getWidth()) < 1 && Math.abs(height - stage.getHeight()) < 1, "course navigation preserves window size");
                    button("Back to Dashboard").fire();
                    check("adminRoot".equals(scene.getRoot().getId()), "Back opens admin dashboard");
                    check(SessionManager.isCurrentSession(identity), "Back preserves exact session");
                    check(Math.abs(width - stage.getWidth()) < 1 && Math.abs(height - stage.getHeight()) < 1, "Back preserves window size");
                }

                var loader = new FXMLLoader(CourseViewSmokeTest.class.getResource("/fxml/courses.fxml"));
                scene.setRoot(loader.load()); layout();
                CourseController controller = loader.getController();
                for (var field : CourseController.class.getDeclaredFields()) {
                    if (field.isAnnotationPresent(FXML.class)) {
                        field.setAccessible(true);
                        check(field.get(controller) != null, "FXML field " + field.getName());
                    }
                }
                dialogs(() -> button("Add Course").fire(), "not ready");
                dialogs(() -> button("Show All").fire(), "not ready");
                dialogs(() -> button("Search").fire(), "not ready");
                dialogs(() -> button("Delete Course").fire(), "not ready");
                var dao = new MemoryDAO();
                controller.setCourseService(new CourseService(dao, CourseService::isCurrentUserAdmin));
                check(dao.reads == 1 && table().getItems().size() == 1, "one initial service load");
                table().getSelectionModel().selectFirst();
                check(field("courseCodeField").getText().equals("ICT101"), "selection fills code");
                check(field("courseNameField").getText().equals("Computing"), "selection fills name");
                check(field("creditsField").getText().equals("3.0"), "selection fills credits");
                check(field("departmentIdField").getText().equals("1"), "selection fills department");
                check(combo().getValue().equals("CORE") && active().isSelected(), "selection fills type/status");
                button("Clear Form").fire();
                check(table().getSelectionModel().isEmpty() && field("courseCodeField").getText().isEmpty(), "clear selection and form");
                dialogs(() -> button("Update Course").fire(), "Select a course");
                fill("NEW101", "New course", "3.5", "1");
                dialogs(() -> button("Add Course").fire(), "added successfully");
                check(table().getItems().size() == 2 && field("courseCodeField").getText().isEmpty(), "add refreshes and clears form");
                field("searchField").setText("NEW101"); button("Search").fire();
                check(table().getItems().size() == 1, "search code");
                field("searchField").setText("New course"); field("searchField").fireEvent(new javafx.event.ActionEvent());
                check(table().getItems().size() == 1, "Enter searches name");
                table().getSelectionModel().selectFirst();
                field("courseNameField").setText("Updated course"); combo().setValue("OPTIONAL"); active().setSelected(false);
                dialogs(() -> button("Update Course").fire(), "updated successfully");
                check(dao.rows.getLast().getCourseName().equals("Updated course") && !dao.rows.getLast().isActive(), "update stores edited values");
                button("Show All").fire();
                check(field("searchField").getText().isEmpty() && table().getItems().size() == 2, "refresh resets search");
                table().getSelectionModel().selectLast();
                dialogs(() -> button("Delete Course").fire(), "CANCEL:Delete NEW101?");
                check(dao.deletes == 0 && table().getItems().size() == 2, "cancel never calls delete");
                dao.errorCode = 1451;
                dialogs(() -> button("Delete Course").fire(), "Delete NEW101?", "academic records");
                check(table().getItems().size() == 2, "constrained deletion leaves table intact");
                dao.errorCode = 0;
                dialogs(() -> button("Delete Course").fire(), "Delete NEW101?", "deleted successfully");
                check(table().getItems().size() == 1, "confirmed delete refreshes table");
                fill("", "Valid name", "3", "1");
                dialogs(() -> button("Add Course").fire(), "Course code is required");
                fill("VALID", "Valid name", "bad", "1");
                dialogs(() -> button("Add Course").fire(), "Enter credits as a number");
                fill("VALID", "Valid name", "3.11", "1");
                dialogs(() -> button("Add Course").fire(), "one decimal place");
                fill("VALID", "Valid name", "3", "bad");
                dialogs(() -> button("Add Course").fire(), "whole number");
                fill("VALID", "Valid name", "3", "1");
                dao.errorCode = 1062; dialogs(() -> button("Add Course").fire(), "already exists");
                dao.errorCode = 1452; dialogs(() -> button("Add Course").fire(), "department does not exist");
                dao.errorCode = 0;
                for (int i = 0; i < 40; i++) dao.rows.add(course(i + 100, "ROW" + i, "Scrolling fixture " + i));
                button("Show All").fire();
                return null;
            });

            for (int[] size : new int[][]{{1024, 600}, {800, 600}, {1366, 700}}) {
                fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                Thread.sleep(250);
                fx(() -> {
                    layout();
                    for (String text : List.of("Back to Dashboard", "Add Course", "Update Course", "Delete Course", "Clear Form")) visible(button(text));
                    for (String id : List.of("courseCodeField", "courseNameField", "creditsField", "departmentIdField", "courseTypeComboBox", "activeCheckBox")) visible(scene.lookup("#" + id));
                    visible(table()); check(table().getHeight() >= 120, "table has usable height");
                    check(table().lookupAll(".scroll-bar").stream().anyMatch(n -> n.isVisible() && n instanceof ScrollBar s
                            && s.getOrientation() == javafx.geometry.Orientation.VERTICAL), "table scrolls vertically");
                    snapshot("courses-" + size[0] + "x" + size[1]);
                    return null;
                });
            }
            fx(() -> {
                button("Back to Dashboard").fire();
                button("Logout").fire();
                check(!SessionManager.isLoggedIn() && scene.lookup("#loginButton") != null, "logout clears session and opens login");
                deniedAccess();
                return null;
            });
            System.out.println("Course JavaFX checks passed: " + checks + "; snapshots in target/course-verification.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void deniedAccess() throws Exception {
        try { new FXMLLoader(CourseViewSmokeTest.class.getResource("/fxml/courses.fxml")).load(); throw new AssertionError("Unauthorized FXML loaded"); }
        catch (javafx.fxml.LoadException e) {
            Throwable cause = e;
            while (cause != null && !(cause instanceof SecurityException)) cause = cause.getCause();
            check(cause instanceof SecurityException, "direct FXML rejects unauthorized user");
        }
        try { NavigationUtil.navigate(new Label(), "/fxml/courses.fxml", "Denied"); throw new AssertionError("Unauthorized navigation"); }
        catch (SecurityException expected) { checks++; }
    }
    private static void dialogs(Runnable action, String... expected) {
        List<String> actual = new ArrayList<>();
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(50), event -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (!window.isShowing() || window.getScene() == null) continue;
                Node pane = window.getScene().getRoot().lookup(".dialog-pane");
                if (pane instanceof DialogPane dialog) {
                    int index = actual.size(); actual.add(dialog.getHeaderText() + " " + dialog.getContentText());
                    boolean cancel = index < expected.length && expected[index].startsWith("CANCEL:");
                    ((Button) dialog.lookupButton(cancel ? ButtonType.CANCEL : ButtonType.OK)).fire();
                }
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE); timer.play();
        try { action.run(); } finally { timer.stop(); }
        check(actual.size() == expected.length, "dialog count: " + actual);
        for (int i = 0; i < expected.length; i++) check(actual.get(i).contains(expected[i].replace("CANCEL:", "")), "dialog message: " + actual.get(i));
    }
    private static void visible(Node node) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        check(node.isVisible() && bounds.getMinX() >= -1 && bounds.getMinY() >= -1
                && bounds.getMaxX() <= scene.getWidth() + 1 && bounds.getMaxY() <= scene.getHeight() + 1, "visible within window: " + node.getId());
    }
    private static void fill(String code, String name, String credits, String department) {
        field("courseCodeField").setText(code); field("courseNameField").setText(name);
        field("creditsField").setText(credits); field("departmentIdField").setText(department);
    }
    private static TextField field(String id) { return (TextField) scene.lookup("#" + id); }
    @SuppressWarnings("unchecked") private static ComboBox<String> combo() { return (ComboBox<String>) scene.lookup("#courseTypeComboBox"); }
    private static CheckBox active() { return (CheckBox) scene.lookup("#activeCheckBox"); }
    @SuppressWarnings("unchecked") private static TableView<Course> table() { return (TableView<Course>) scene.lookup("#courseTable"); }
    private static void layout() { scene.getRoot().applyCss(); scene.getRoot().layout(); }
    private static Button button(String text) {
        layout(); return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + text));
    }
    private static void snapshot(String name) throws Exception {
        var image = scene.getRoot().snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight(); int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(Path.of("target/course-verification"));
        ImageIO.write(output, "png", Path.of("target/course-verification", name + ".png").toFile());
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); checks++; }
    private static Course course(int id, String code, String name) {
        Course c = new Course(); c.setCourseId(id); c.setCourseCode(code); c.setCourseName(name);
        c.setCredits(new BigDecimal("3.0")); c.setDepartmentId(1); c.setCourseType("CORE"); c.setActive(true); return c;
    }
    private static final class MemoryDAO extends CourseDAO {
        final List<Course> rows = new ArrayList<>(List.of(course(1, "ICT101", "Computing")));
        int reads, deletes, errorCode;
        private void fail() throws SQLException { if (errorCode != 0) throw new SQLException("Raw SQL detail must not reach the user", "23000", errorCode); }
        @Override public List<Course> findAll() { reads++; return List.copyOf(rows); }
        @Override public List<Course> search(String keyword) { return rows.stream().filter(c -> c.getCourseCode().contains(keyword) || c.getCourseName().contains(keyword)).toList(); }
        @Override public int insert(Course course) throws SQLException { fail(); course.setCourseId(2); rows.add(course); return 2; }
        @Override public boolean update(Course course) throws SQLException { fail(); rows.removeIf(c -> c.getCourseId() == course.getCourseId()); rows.add(course); return true; }
        @Override public boolean delete(int id) throws SQLException { deletes++; fail(); return rows.removeIf(c -> c.getCourseId() == id); }
    }
}
