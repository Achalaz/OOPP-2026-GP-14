package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.util.List;
import java.util.concurrent.*;

/** Read-only live-MySQL navigation runner; uses an active account without changing credentials or records. */
public final class LecturerNavigationSmokeTest {
    private static Scene scene;
    private static Stage stage;
    private static int checks;

    public static void main(String[] args) throws Exception {
        List<User> users = new UserDAO().getAllUsers();
        User lecturer = users.stream().filter(user -> user.isActive() && user.getRole().equals("LECTURER"))
                .findFirst().orElseThrow(() -> new AssertionError("An active lecturer is required in the test database."));
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX startup");
        try {
            fx(() -> {
                Platform.setImplicitExit(false);
                SessionManager.startSession(lecturer);
                scene = new Scene(FXMLLoader.load(LecturerNavigationSmokeTest.class.getResource("/fxml/lecturer_dashboard.fxml")), 1100, 720);
                stage = new Stage(); stage.setOpacity(0); stage.setScene(scene); stage.show();
                return null;
            });
            var identity = SessionManager.requireSession();
            for (String panel : List.of("Upload Marks", "Upload Exam Marks", "Course Materials", "Undergraduates",
                    "Student Eligibility", "Marks and Grades", "Reports", "GPA", "Attendance Records", "Medical", "My Profile")) {
                fx(() -> { button(panel).fire(); return null; });
                awaitReady();
                checkBackAtEverySize(panel);
                fx(() -> {
                    back().fire();
                    check("lecturerRoot".equals(scene.getRoot().getId()), panel + " returns to lecturer dashboard");
                    check(SessionManager.isCurrentSession(identity), panel + " preserves exact login session");
                    return null;
                });
            }
            // The shared workspace also retains its older marks mode.
            fx(() -> {
                var loader = new FXMLLoader(LecturerNavigationSmokeTest.class.getResource("/fxml/lecturer_workspace.fxml"));
                scene.setRoot(loader.load());
                ((LecturerWorkspaceController) loader.getController()).configure("marks", "Exam Marks");
                return null;
            });
            awaitReady();
            checkBackAtEverySize("workspace marks mode");
            fx(() -> {
                back().fire();
                check("lecturerRoot".equals(scene.getRoot().getId()), "shared marks mode dashboard return");
                check(SessionManager.isCurrentSession(identity), "shared marks mode preserves session");
                return null;
            });
            // Both existing links from the original Marks screen use the authorized workspace.
            for (String panel : List.of("Eligibility", "Results & Grades")) {
                fx(() -> { button("Upload Marks").fire(); button(panel).fire(); return null; });
                awaitReady();
                fx(() -> {
                    back().fire();
                    check("lecturerRoot".equals(scene.getRoot().getId()), "marks to " + panel + " to dashboard");
                    check(SessionManager.isCurrentSession(identity), "academic navigation preserves session");
                    return null;
                });
            }
            // Check all three Logout entry points with newly established test sessions.
            for (String panel : List.of("Course Materials", "My Profile", "Dashboard")) {
                fx(() -> {
                    SessionManager.startSession(lecturer);
                    NavigationUtil.navigate(scene.getRoot(), "/fxml/lecturer_dashboard.fxml", "Lecturer Dashboard");
                    if (!panel.equals("Dashboard")) button(panel).fire();
                    return null;
                });
                if (!panel.equals("Dashboard")) awaitReady();
                fx(() -> {
                    button(panel.equals("My Profile") ? "Log out" : "Log Out").fire();
                    check(!SessionManager.isLoggedIn(), panel + " Logout clears session");
                    check(scene.getRoot().lookup("#loginButton") != null, panel + " Logout opens login");
                    return null;
                });
            }
            // Shared profile must preserve routing for every existing role; shared Marks also supports ADMIN.
            for (String role : List.of("ADMIN", "STUDENT", "TECHNICAL_OFFICER")) {
                User user = users.stream().filter(candidate -> candidate.isActive() && candidate.getRole().equals(role))
                        .findFirst().orElseThrow(() -> new AssertionError("Test database needs active role: " + role));
                for (String resource : role.equals("ADMIN")
                        ? List.of("/fxml/profile.fxml", "/fxml/marks_entry.fxml") : List.of("/fxml/profile.fxml")) {
                    fx(() -> { SessionManager.startSession(user); NavigationUtil.navigate(scene.getRoot(), resource, "Shared screen"); return null; });
                    awaitReady();
                    var sharedIdentity = SessionManager.requireSession();
                    fx(() -> {
                        back().fire();
                        check(SessionManager.isCurrentSession(sharedIdentity), role + " shared screen preserves session");
                        check(role.equals("ADMIN") ? "adminRoot".equals(scene.getRoot().getId())
                                : scene.getRoot().lookup("#nameLabel") != null, role + " returns to its own dashboard");
                        return null;
                    });
                }
            }
            System.out.println("Lecturer navigation: " + checks + " checks passed; live MySQL reads only, all modes, role routing, Logout and resizing.");
            System.out.println("Credential-entry login was not exercised: test sessions use existing active database users.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void awaitReady() throws Exception {
        await(() -> {
            var refresh = scene.getRoot().lookup("#refresh");
            if (refresh == null) refresh = scene.getRoot().lookup("#refreshButton");
            return refresh == null || !refresh.isDisabled();
        });
        fx(() -> {
            check(back().getOnAction() != null && !back().isDisabled(), "Back handler is available");
            var offerings = (ComboBox<?>) scene.getRoot().lookup("#offering");
            if (offerings != null) check(!offerings.getItems().isEmpty(), "workspace loaded assigned offerings");
            var status = (Label) scene.getRoot().lookup("#statusLabel");
            if (status != null) check(!status.getStyleClass().contains("status-error"), "profile loaded successfully");
            return null;
        });
    }

    private static void checkBackAtEverySize(String panel) throws Exception {
        for (int[] size : List.of(new int[]{320, 280}, new int[]{640, 480}, new int[]{1024, 720}, new int[]{1400, 900})) {
            fx(() -> {
                stage.setWidth(size[0] + stage.getWidth() - scene.getWidth());
                stage.setHeight(size[1] + stage.getHeight() - scene.getHeight());
                return null;
            });
            await(() -> Math.abs(scene.getWidth() - size[0]) <= 2 && Math.abs(scene.getHeight() - size[1]) <= 2);
            fx(() -> {
                scene.getRoot().applyCss(); scene.getRoot().layout();
                Button back = back();
                var bounds = back.localToScene(back.getBoundsInLocal());
                check(back.isVisible() && back.isManaged(), panel + " Back is visible");
                check(bounds.getMinX() >= 0 && bounds.getMinY() >= 0 && bounds.getMaxX() <= scene.getWidth()
                        && bounds.getMaxY() <= Math.min(100, scene.getHeight()), panel + " Back fits near the top at " + size[0]);
                check(back.getWidth() >= back.prefWidth(-1) - 1, panel + " Back label is not clipped");
                check(back.getText().equals("\u2190 Back to Dashboard"), panel + " consistent label");
                check(scene.getRoot().lookupAll(".button").stream().filter(node -> node instanceof Button b
                        && b.getText().equals(back.getText())).count() == 1, panel + " has one Back button");
                return null;
            });
        }
    }

    private static Button back() { return (Button) scene.getRoot().lookup("#dashboardButton"); }
    private static Button button(String text) {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return scene.getRoot().lookupAll(".button").stream().filter(node -> node instanceof Button b && b.getText().strip().equals(text))
                .map(node -> (Button) node).findFirst().orElseThrow(() -> new AssertionError("Button missing: " + text));
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 300; i++) { if (fx(condition)) return; Thread.sleep(50); }
        throw new AssertionError("Navigation/layout timed out");
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
}
