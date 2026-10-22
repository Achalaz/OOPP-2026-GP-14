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
import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/** Real JavaFX actions with memory CRUD. --live-read also exercises production navigation and dropdown queries. */
public final class CourseOfferingViewSmokeTest {
    private static int checks;
    private static Scene scene;
    private static Stage stage;

    public static void main(String[] args) throws Exception {
        boolean live = Arrays.equals(args, new String[]{"--live-read"});
        User admin = live ? new UserDAO().getAllUsers().stream()
                .filter(u -> u.isActive() && "ADMIN".equals(u.getRole())).findFirst().orElseThrow()
                : new User(1, "fixture", "Fixture", "", "ADMIN", true);
        var liveDao = new CourseOfferingDAO();
        int offerings = live ? liveDao.getAllOfferings().size() : 0;
        int courses = live ? liveDao.getActiveCourses().size() : 0;
        int semesters = live ? liveDao.getSemesters().size() : 0;
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX started");
        try {
            fx(() -> {
                System.out.println("Offering JavaFX: authorization and dashboard FXML");
                Platform.setImplicitExit(false);
                SessionManager.logout(); denied();
                for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
                    SessionManager.startSession(new User(1, "fixture", "Fixture", "", role, true)); denied();
                }
                SessionManager.startSession(admin);
                var dashboard = new FXMLLoader(CourseOfferingViewSmokeTest.class.getResource("/fxml/admin_dashboard.fxml"));
                scene = new Scene(dashboard.load(), 1024, 600);
                verifyFields(dashboard);
                stage = new Stage(); stage.setScene(scene); stage.setOpacity(0); stage.show();
                var identity = SessionManager.requireSession();
                check(NavigationUtil.dashboardResource().equals("/fxml/admin_dashboard.fxml"), "authenticated Admin destination");
                if (live) {
                    double width = stage.getWidth(), height = stage.getHeight();
                    for (boolean card : new boolean[]{false, true}) {
                        System.out.println("Offering JavaFX: live " + (card ? "card" : "sidebar") + " navigation");
                        dialogs(() -> entry("Course Offerings", card).fire()); layout();
                        check("courseOfferingsRoot".equals(scene.getRoot().getId()), "dashboard " + (card ? "card" : "sidebar") + " opens offerings");
                        check(table().getItems().size() == offerings, "production service loads actual offerings");
                        check(combo("courseCombo").getItems().size() == courses && courses > 0, "actual active course dropdown");
                        check(combo("semesterCombo").getItems().size() == semesters && semesters > 0, "actual semester dropdown");
                        check(stage.getScene() == scene, "same scene and window");
                        check(Math.abs(stage.getWidth() - width) < 1 && Math.abs(stage.getHeight() - height) < 1, "opening retains window size");
                        dialogs(() -> button("Back to Admin Dashboard").fire());
                        check("adminRoot".equals(scene.getRoot().getId()) && SessionManager.isCurrentSession(identity), "Back retains exact session");
                        check(Math.abs(stage.getWidth() - width) < 1 && Math.abs(stage.getHeight() - height) < 1, "Back retains window size");
                    }
                    System.out.println("Offering JavaFX: existing Admin navigation");
                    dialogs(() -> entry("Courses", false).fire());
                    check("coursesRoot".equals(scene.getRoot().getId()) && scene.lookup("#courseTable") != null, "existing Course Management navigation");
                    dialogs(() -> button("Back to Dashboard").fire());
                    dialogs(() -> entry("Notices", false).fire());
                    check("noticesRoot".equals(scene.getRoot().getId()), "existing Notices navigation");
                    NavigationUtil.navigate(scene.getRoot(), "/fxml/admin_dashboard.fxml", "Admin Dashboard");
                    dialogs(() -> entry("User Profiles", false).fire());
                    check(scene.lookup("#usersTable") != null, "existing User Profiles navigation");
                    NavigationUtil.navigate(scene.getRoot(), "/fxml/admin_dashboard.fxml", "Admin Dashboard");
                    dialogs(() -> entry("Timetables", false).fire(), "will be connected");
                    dialogs(() -> entry("Dashboard", false).fire());
                    check("adminRoot".equals(scene.getRoot().getId()), "existing Dashboard navigation");
                    dialogs(() -> button("My Profile").fire());
                    check("profileRoot".equals(scene.getRoot().getId()), "existing Profile navigation");
                    NavigationUtil.navigate(scene.getRoot(), "/fxml/admin_dashboard.fxml", "Admin Dashboard");
                }
                System.out.println("Offering JavaFX: memory CRUD and controller bindings");
                var loader = new FXMLLoader(CourseOfferingViewSmokeTest.class.getResource("/fxml/course_offerings.fxml"));
                scene.setRoot(loader.load()); layout(); verifyFields(loader);
                System.out.println("Offering JavaFX: injecting memory service");
                CourseOfferingController controller = loader.getController();
                var dao = new MemoryDAO();
                var service = new CourseOfferingService(dao, CourseService::isCurrentUserAdmin);
                controller.setCourseOfferingService(service);
                System.out.println("Offering JavaFX: selection and form checks");
                check(dao.reads == 1 && dao.courses == 1 && dao.semesters == 1, "one initial load of table and dropdowns");
                try { controller.setCourseOfferingService(service); throw new AssertionError("Repeated injection accepted"); }
                catch (IllegalStateException expected) { checks++; }
                check(dao.reads == 1, "repeated injection never reloads");
                check(combo("statusCombo").getItems().equals(List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED")), "exact supported statuses");
                table().getSelectionModel().selectFirst();
                check(field("groupField").getText().equals("A"), "selection populates form");
                table().getSelectionModel().selectLast();
                check(((CourseOfferingDAO.CourseOption) combo("courseCombo").getValue()).courseId() == 99, "inactive course selection keeps correct identity");
                check(combo("courseCombo").getItems().size() == 1, "inactive course not added to active dropdown");
                button("Clear Form").fire();
                check(table().getSelectionModel().isEmpty() && combo("courseCombo").getValue() == null
                        && combo("semesterCombo").getValue() == null && field("groupField").getText().isEmpty()
                        && combo("statusCombo").getValue().equals("PLANNED"), "Clear resets complete form");
                dialogs(() -> button("Save Offering").fire(), "Select a course and semester");
                fill("B"); dialogs(() -> button("Save Offering").fire(), "created successfully");
                check(table().getItems().size() == 3 && field("groupField").getText().isEmpty(), "create refreshes and clears");
                table().getSelectionModel().selectLast(); field("groupField").setText("C"); combo("statusCombo").setValue("COMPLETED");
                dialogs(() -> button("Save Offering").fire(), "updated successfully");
                check(dao.rows.getLast().getOfferingGroup().equals("C") && dao.rows.getLast().getStatus().equals("COMPLETED"), "edit existing offering");
                field("searchField").setText("C"); field("searchField").fireEvent(new javafx.event.ActionEvent());
                check(table().getItems().size() == 1, "Enter searches");
                table().getSelectionModel().selectFirst(); field("searchField").setText("missing"); button("Search").fire();
                check(table().getItems().isEmpty() && field("groupField").getText().isEmpty(), "Search resets stale edit selection");
                button("Refresh").fire();
                check(table().getItems().size() == 3 && field("searchField").getText().isEmpty(), "Refresh restores all rows and clears search");
                table().getSelectionModel().selectLast();
                dialogs(() -> button("Delete Selected").fire(), "CANCEL:Delete selected offering?");
                check(dao.deletes == 0, "cancel never calls DAO");
                dao.error = 1451;
                dialogs(() -> button("Delete Selected").fire(), "Delete selected offering?", "linked to academic records");
                check(table().getItems().size() == 3, "linked deletion retains rows");
                dao.error = 0;
                dialogs(() -> button("Delete Selected").fire(), "Delete selected offering?", "deleted successfully");
                check(table().getItems().size() == 2, "confirmed delete refreshes table");
                fill("D"); dao.error = 1062; dialogs(() -> button("Save Offering").fire(), "already exists");
                dao.error = 1452; dialogs(() -> button("Save Offering").fire(), "no longer exists"); dao.error = 0;
                SessionManager.logout(); int saves = dao.saves;
                dialogs(() -> button("Save Offering").fire(), "Only Admin users");
                check(dao.saves == saves, "expired screen rejects writes before DAO");
                SessionManager.startSession(admin);
                for (int i = 0; i < 40; i++) dao.rows.add(offering(100 + i, 1, "ROW" + i));
                button("Refresh").fire();
                return null;
            });
            for (int[] size : new int[][]{{1024, 600}, {800, 600}, {1366, 700}}) {
                System.out.println("Offering JavaFX: layout " + size[0] + "x" + size[1]);
                fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                Thread.sleep(250);
                fx(() -> {
                    var scroll = (ScrollPane) scene.getRoot(); scroll.setVvalue(0); layout();
                    for (String name : List.of("Back to Admin Dashboard", "Save Offering", "Clear Form", "Delete Selected", "Search", "Refresh")) {
                        Node node = button(name); var bounds = node.localToScene(node.getBoundsInLocal());
                        check(bounds.getMinX() >= 0 && bounds.getMaxX() <= scene.getWidth(), "button fits horizontally: " + name);
                    }
                    for (String name : List.of("courseCombo", "semesterCombo", "groupField", "statusCombo")) {
                        Node node = scene.lookup("#" + name); var bounds = node.localToScene(node.getBoundsInLocal());
                        check(bounds.getMinX() >= 0 && bounds.getMaxX() <= scene.getWidth(), "form control fits: " + name);
                    }
                    check(table().getHeight() >= 220, "table retains usable height");
                    snapshot("offerings-" + size[0] + "x" + size[1]);
                    scroll.setVvalue(1); layout();
                    check(table().localToScene(table().getBoundsInLocal()).getMaxY() <= scene.getHeight(), "table bottom reachable by scrolling");
                    return null;
                });
            }
            fx(() -> {
                var identity = SessionManager.requireSession(); button("Back to Admin Dashboard").fire();
                check(SessionManager.isCurrentSession(identity), "Back preserves session after CRUD");
                snapshot("admin-offerings"); button("Logout").fire();
                check(!SessionManager.isLoggedIn() && scene.lookup("#loginButton") != null, "Logout still opens login and clears session");
                denied(); return null;
            });
            System.out.println("Course Offering JavaFX checks passed: " + checks + "; live read navigation=" + live + "; CRUD uses memory DAO.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void denied() throws Exception {
        try { new FXMLLoader(CourseOfferingViewSmokeTest.class.getResource("/fxml/course_offerings.fxml")).load(); throw new AssertionError("Unauthorized FXML loaded"); }
        catch (javafx.fxml.LoadException error) {
            Throwable cause = error; while (cause != null && !(cause instanceof SecurityException)) cause = cause.getCause();
            check(cause instanceof SecurityException, "direct FXML refuses unauthorized session");
        }
        try { NavigationUtil.navigate(new Label(), "/fxml/course_offerings.fxml", "Denied"); throw new AssertionError("Unauthorized navigation"); }
        catch (SecurityException expected) { checks++; }
    }
    private static void verifyFields(FXMLLoader loader) throws Exception {
        Object controller = loader.getController();
        for (var field : controller.getClass().getDeclaredFields()) if (field.isAnnotationPresent(FXML.class)) {
            field.setAccessible(true); check(field.get(controller) != null, "injected fx:id " + field.getName());
        }
    }
    private static void dialogs(Runnable action, String... expected) {
        System.out.println("Offering JavaFX dialog check: " + Arrays.toString(expected));
        List<String> actual = new ArrayList<>();
        Set<DialogPane> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(50), event -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (!window.isShowing() || window.getScene() == null) continue;
                Node pane = window.getScene().getRoot().lookup(".dialog-pane");
                if (pane instanceof DialogPane dialog && handled.add(dialog)) {
                    int index = actual.size(); actual.add(dialog.getHeaderText() + " " + dialog.getContentText());
                    boolean cancel = index < expected.length && expected[index].startsWith("CANCEL:");
                    // Complete the animation pulse before closing a modal dialog: the
                    // controller may immediately open a second result/error dialog.
                    Platform.runLater(() -> ((Button) dialog.lookupButton(cancel ? ButtonType.CANCEL : ButtonType.OK)).fire());
                }
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE); timer.play();
        try { action.run(); } finally { timer.stop(); }
        check(actual.size() == expected.length, "dialog count: " + actual);
        for (int i = 0; i < expected.length; i++) check(actual.get(i).contains(expected[i].replace("CANCEL:", "")), "dialog message: " + actual.get(i));
    }
    private static void fill(String group) { combo("courseCombo").getSelectionModel().selectFirst(); combo("semesterCombo").getSelectionModel().selectFirst(); field("groupField").setText(group); }
    private static TextField field(String id) { return (TextField) scene.lookup("#" + id); }
    @SuppressWarnings("unchecked") private static ComboBox<Object> combo(String id) { return (ComboBox<Object>) scene.lookup("#" + id); }
    @SuppressWarnings("unchecked") private static TableView<CourseOffering> table() { return (TableView<CourseOffering>) scene.lookup("#offeringTable"); }
    private static void layout() { scene.getRoot().applyCss(); scene.getRoot().layout(); }
    private static List<Button> buttons() { layout(); return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).toList(); }
    private static Button button(String text) { return buttons().stream().filter(b -> b.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + text)); }
    private static Button entry(String text, boolean card) { return buttons().stream().filter(b -> b.getText().contains(text) && b.getText().contains("Manage") == card).findFirst().orElseThrow(); }
    private static void snapshot(String name) throws Exception {
        var image = scene.getRoot().snapshot(null, null); int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); output.setRGB(0, 0, w, h, pixels, 0, w);
        Files.createDirectories(Path.of("target/course-offering-verification"));
        ImageIO.write(output, "png", Path.of("target/course-offering-verification", name + ".png").toFile());
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task);
        try { return task.get(45, TimeUnit.SECONDS); }
        catch (TimeoutException error) {
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                if (thread.getName().contains("JavaFX")) {
                    System.err.println(thread.getName());
                    for (var frame : stack) System.err.println("  at " + frame);
                }
            });
            throw error;
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static CourseOffering offering(int id, int course, String group) {
        CourseOffering o = new CourseOffering(); o.setOfferingId(id); o.setCourseId(course); o.setCourseCode("ICT" + course); o.setCourseName("Computing " + course);
        o.setCredits(3); o.setSemesterId(2); o.setSemesterName("Semester 2"); o.setAcademicYearName("2026"); o.setOfferingGroup(group); o.setStatus("PLANNED"); return o;
    }
    private static final class MemoryDAO extends CourseOfferingDAO {
        final List<CourseOffering> rows = new ArrayList<>(List.of(offering(7, 1, "A"), offering(8, 99, "INACTIVE")));
        int reads, courses, semesters, saves, deletes, error;
        private void fail() throws SQLException { if (error != 0) throw new SQLException("Raw SQL must not be shown", "23000", error); }
        @Override public List<CourseOffering> getAllOfferings() { reads++; return List.copyOf(rows); }
        @Override public List<CourseOffering> searchOfferings(String keyword) { return rows.stream().filter(o -> o.getOfferingGroup().equals(keyword)).toList(); }
        @Override public List<CourseOption> getActiveCourses() { courses++; return List.of(new CourseOption(1, "ICT1", "Computing 1", 3)); }
        @Override public List<SemesterOption> getSemesters() { semesters++; return List.of(new SemesterOption(2, "Semester 2 - 2026")); }
        @Override public boolean saveOffering(CourseOffering o) throws SQLException {
            saves++; fail(); if (o.getOfferingId() == 0) o.setOfferingId(9);
            o.setCourseCode("ICT" + o.getCourseId()); rows.removeIf(row -> row.getOfferingId() == o.getOfferingId()); rows.add(o); return true;
        }
        @Override public boolean deleteOffering(int id) throws SQLException { deletes++; fail(); return rows.removeIf(o -> o.getOfferingId() == id); }
    }
}
