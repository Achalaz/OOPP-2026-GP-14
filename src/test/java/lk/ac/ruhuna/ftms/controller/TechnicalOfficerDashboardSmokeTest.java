package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lk.ac.ruhuna.ftms.dao.ProfileDAO;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.ProfileService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit main runner. Default: no database. --live-profile: existing account reads only.
 * Test sessions are fixtures, not a login mechanism or a preview mode in the application. */
public final class TechnicalOfficerDashboardSmokeTest {
    private static final String RESOURCE = "/fxml/technical_officer_dashboard.fxml";
    private static final String ROLE = "TECHNICAL_OFFICER";
    private static int checks;
    private static Stage stage;
    private static Scene scene;
    private static Parent root;

    public static void main(String[] args) throws Exception {
        boolean snapshots = List.of(args).contains("--snapshot");
        boolean live = List.of(args).contains("--live-profile");
        User liveOfficer = live ? new UserDAO().getAllUsers().stream()
                .filter(user -> user.isActive() && ROLE.equals(user.getRole()))
                .findFirst().orElseThrow(() -> new AssertionError("Live test needs an existing active Technical Officer.")) : null;
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        check(ready.await(10, TimeUnit.SECONDS), "JavaFX starts");
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                SessionManager.logout();
                deniedLoad();
                for (var route : Map.of("ADMIN", "/fxml/admin_dashboard.fxml", "LECTURER",
                        "/fxml/lecturer_dashboard.fxml", "STUDENT", "/fxml/student_dashboard.fxml").entrySet()) {
                    SessionManager.startSession(user(route.getKey()));
                    check(NavigationUtil.dashboardResource().equals(route.getValue()), route.getKey() + " routing unchanged");
                    new FXMLLoader(TechnicalOfficerDashboardSmokeTest.class.getResource(route.getValue())).load();
                    deniedLoad();
                }
                User inactive = user(ROLE);
                inactive.setActive(false);
                try { SessionManager.startSession(inactive); throw new AssertionError("Inactive session allowed"); }
                catch (IllegalArgumentException expected) { checks++; }
                SessionManager.startSession(user(ROLE));
                check(NavigationUtil.dashboardResource().equals(RESOURCE), "Technical Officer route");
                FXMLLoader loader = new FXMLLoader(TechnicalOfficerDashboardSmokeTest.class.getResource(RESOURCE));
                root = loader.load();
                for (var field : TechnicalOfficerDashboardController.class.getDeclaredFields()) {
                    if (field.isAnnotationPresent(FXML.class)) {
                        field.setAccessible(true);
                        check(field.get(loader.getController()) != null, "injected " + field.getName());
                    }
                }
                stage = new Stage();
                scene = new Scene(root, 1100, 690);
                stage.setScene(scene);
                stage.setTitle("FTMS - Technical Officer UI verification");
                stage.setOpacity(0);
                stage.show();
                for (String forbidden : List.of("Users.fxml", "attendance.fxml", "medical.fxml", "marks_entry.fxml",
                        "results.fxml", "gpa.fxml", "reports.fxml", "eligibility.fxml", "lecturer_workspace.fxml",
                        "admin_dashboard.fxml", "lecturer_dashboard.fxml", "student_dashboard.fxml")) {
                    try {
                        NavigationUtil.navigate(root, "/fxml/" + forbidden, "Not permitted");
                        throw new AssertionError("Technical Officer navigation allowed: " + forbidden);
                    } catch (SecurityException expected) {
                        check(scene.getRoot() == root, "forbidden navigation preserves current screen");
                    }
                }
                return null;
            });

            for (int[] size : List.of(new int[]{1280, 720}, new int[]{1100, 690}, new int[]{900, 600},
                    new int[]{760, 540}, new int[]{640, 480})) {
                fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                settle();
                fx(() -> {
                    root.applyCss(); root.layout();
                    ScrollPane scroll = (ScrollPane) root.lookup("#contentScroll");
                    GridPane grid = (GridPane) root.lookup("#featureGrid");
                    check(grid.getChildren().size() == 5, "five cards");
                    check(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1,
                            "content fits viewport at " + size[0]);
                    for (Node card : grid.getChildren()) {
                        check(card.getLayoutX() + card.getLayoutBounds().getWidth() <= grid.getWidth() + 1, "card fits grid");
                        for (Node child : ((Parent) card).getChildrenUnmodifiable()) {
                            check(child.getBoundsInParent().getMaxX() <= card.getLayoutBounds().getWidth() + 1, "card child fits width");
                            check(child.getBoundsInParent().getMaxY() <= card.getLayoutBounds().getHeight() + 1, "card child fits height");
                        }
                    }
                    for (Node node : root.lookupAll(".nav-button")) {
                        Button button = (Button) node;
                        javafx.scene.text.Text text = (javafx.scene.text.Text) button.lookup(".text");
                        check(text != null && text.getText().equals(button.getText()), "sidebar text is not truncated");
                    }
                    Button logout = (Button) root.lookup("#logoutButton");
                    check(logout.localToScene(logout.getBoundsInLocal()).getMaxY() <= scene.getHeight(), "logout remains visible");
                    check(stage.getStyle() == StageStyle.DECORATED && !stage.isFullScreen(), "native title bar retained");
                    System.out.println("Layout " + size[0] + "x" + size[1] + ": " + grid.getColumnConstraints().size() + " columns");
                    if (snapshots && (size[0] == 1280 || size[0] == 900)) {
                        snapshot(root, "technical-officer-" + size[0]);
                        if (size[0] == 1280) snapshot(grid, "technical-officer-all-cards");
                    }
                    return null;
                });
            }
            fx(() -> { stage.setWidth(1100); stage.setHeight(690); return null; });
            settle();
            fx(() -> {
                var identity = SessionManager.requireSession();
                List<Button> placeholders = root.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(button -> !List.of("Dashboard", "Logout", "My Profile").contains(button.getText())).toList();
                check(placeholders.size() == 8, "four sidebar and four card placeholder actions");
                for (Button button : placeholders) {
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Platform.runLater(() -> {
                        try {
                            Window popup = Window.getWindows().stream().filter(window -> window != stage && window.isShowing()).findFirst().orElseThrow();
                            DialogPane pane = (DialogPane) popup.getScene().getRoot();
                            check(pane.getContentText().equals("This feature will be available soon."), "placeholder message");
                            check(pane.getHeaderText().contains("Coming Soon"), "Coming Soon heading");
                            ((Button) pane.lookupButton(ButtonType.OK)).fire();
                        } catch (Throwable error) {
                            failure.set(error);
                            for (Window popup : new ArrayList<>(Window.getWindows())) if (popup != stage) popup.hide();
                        }
                    });
                    button.fire();
                    if (failure.get() != null) throw new AssertionError("Dialog verification failed", failure.get());
                    check(scene.getRoot() == root && SessionManager.isCurrentSession(identity), "placeholder retains session/screen");
                }
                ScrollPane scroll = (ScrollPane) root.lookup("#contentScroll");
                scroll.setVvalue(1);
                findButton("Dashboard").fire();
                check(scroll.getVvalue() == 0 && SessionManager.isCurrentSession(identity), "Dashboard preserves login and resets scroll");
                double width = stage.getWidth(), height = stage.getHeight();
                NavigationUtil.navigate(root, RESOURCE, "FTMS - Technical Officer Dashboard");
                check(Math.abs(stage.getWidth() - width) < 2 && Math.abs(stage.getHeight() - height) < 2, "navigation preserves window size");
                root = scene.getRoot(); root.applyCss(); root.layout();
                SessionManager.startSession(user(ROLE));
                var newerIdentity = SessionManager.requireSession();
                findButton("Logout").fire();
                check(SessionManager.isCurrentSession(newerIdentity), "old dashboard cannot log out a newer session");
                findButton("Manage Attendance").fire();
                check(((Label) root.lookup("#statusLabel")).getText().contains("session"), "stale feature action rejected");
                NavigationUtil.navigate(root, RESOURCE, "FTMS - Technical Officer Dashboard");
                root = scene.getRoot(); root.applyCss(); root.layout();
                findButton("Logout").fire();
                check(!SessionManager.isLoggedIn() && scene.getRoot().lookup("#loginButton") != null, "logout clears session and opens login");
                deniedLoad();
                return null;
            });

            // Shared profile, injected owner-bound read fixture: no SQL or file writes.
            fx(() -> {
                SessionManager.startSession(user(ROLE));
                FXMLLoader loader = new FXMLLoader(TechnicalOfficerDashboardSmokeTest.class.getResource("/fxml/profile.fxml"));
                loader.setControllerFactory(type -> new ProfileController(new ProfileService(new ProfileDAO() {
                    @Override public User getProfile(SessionManager.SessionIdentity identity) {
                        SessionManager.requireSession(identity);
                        return SessionManager.getCurrentUser();
                    }
                })));
                scene.setRoot(loader.load());
                return null;
            });
            awaitProfile();
            checkProfileAndBack();

            if (live) {
                // Real My Profile forward/back actions, with existing database data and no saves.
                for (boolean sidebar : List.of(true, false)) {
                    fx(() -> {
                        SessionManager.startSession(liveOfficer);
                        NavigationUtil.navigate(scene.getRoot(), RESOURCE, "FTMS - Technical Officer Dashboard");
                        root = scene.getRoot(); root.applyCss(); root.layout();
                        Button profile = root.lookupAll(sidebar ? ".nav-button" : ".card-button").stream()
                                .filter(Button.class::isInstance).map(Button.class::cast)
                                .filter(button -> button.getText().equals("My Profile")).findFirst().orElseThrow();
                        profile.fire();
                        check("profileRoot".equals(scene.getRoot().getId()), "real My Profile button opens existing screen");
                        return null;
                    });
                    awaitProfile();
                    checkProfileAndBack();
                }
                fx(() -> { findButton("My Profile").fire(); return null; });
                awaitProfile();
                fx(() -> {
                    double width = stage.getWidth(), height = stage.getHeight();
                    ((Button) scene.getRoot().lookup("#logoutButton")).fire();
                    check(!SessionManager.isLoggedIn(), "shared profile logout clears session");
                    check(scene.getRoot().lookup("#loginButton") != null, "profile logout opens login");
                    check(Math.abs(stage.getWidth() - width) < 2 && Math.abs(stage.getHeight() - height) < 2, "profile logout retains window size");
                    return null;
                });
            }
            System.out.println("PASS: " + checks + " Technical Officer dashboard checks. "
                    + (live ? "Existing profile database reads exercised; no writes." : "No database access."));
            System.out.println("Credential-entry login was not tested; authentication was not bypassed in application code.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void checkProfileAndBack() throws Exception {
        fx(() -> {
            Parent profile = scene.getRoot(); profile.applyCss(); profile.layout();
            var identity = SessionManager.requireSession();
            check(!((TextField) profile.lookup("#usernameField")).isEditable(), "profile username read-only");
            check(!((TextField) profile.lookup("#roleField")).isEditable(), "profile role read-only");
            check(profile.lookupAll(".password-field").isEmpty(), "no profile password editor");
            check(((TextField) profile.lookup("#fullNameField")).isEditable(), "personal name editable");
            ((Button) profile.lookup("#dashboardButton")).fire();
            root = scene.getRoot(); root.applyCss(); root.layout();
            check("technicalOfficerRoot".equals(root.getId()), "profile Back returns to officer dashboard");
            check(SessionManager.isCurrentSession(identity), "Back preserves login identity");
            return null;
        });
    }

    private static void awaitProfile() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(() -> {
            Button refresh = (Button) scene.getRoot().lookup("#refreshButton");
            return refresh != null && !refresh.isDisabled();
        })) {
            if (System.nanoTime() > deadline) throw new AssertionError("Profile load timed out");
            Thread.sleep(25);
        }
        fx(() -> { check(!((Button) scene.getRoot().lookup("#saveButton")).isDisabled(), "profile loaded successfully"); return null; });
    }
    private static User user(String role) { return new User(1, "ui-fixture", "Technical Officer", "officer@example.org", role, true); }
    private static void deniedLoad() throws Exception {
        try { new FXMLLoader(TechnicalOfficerDashboardSmokeTest.class.getResource(RESOURCE)).load(); throw new AssertionError("Unauthorized FXML loaded"); }
        catch (javafx.fxml.LoadException error) {
            Throwable cause = error;
            while (cause != null && !(cause instanceof SecurityException)) cause = cause.getCause();
            check(cause instanceof SecurityException, "unauthorized officer FXML denied");
        }
    }
    private static Button findButton(String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }
    private static void snapshot(Parent parent, String name) throws Exception {
        var image = parent.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        Path directory = Path.of("target", "technical-officer-ui-check");
        Files.createDirectories(directory);
        ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
    private static void settle() throws Exception {
        CountDownLatch pulse = new CountDownLatch(1);
        fx(() -> {
            var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
            pause.setOnFinished(event -> pulse.countDown()); pause.play(); return null;
        });
        check(pulse.await(5, TimeUnit.SECONDS), "window resize completed");
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
