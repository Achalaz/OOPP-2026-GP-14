package lk.ac.ruhuna.ftms.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.service.AttendanceDatabaseFixture;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Real FXML/actions/JDBC. Writes only to isolated temporary tables. No application login bypass. */
public final class AttendanceViewSmokeTest {
    private static int checks;
    private static Stage stage;
    private static Scene scene;
    private static Parent root;

    public static void main(String[] args) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        check(ready.await(10, TimeUnit.SECONDS), "JavaFX started");
        Platform.setImplicitExit(false);
        try (var fixture = new AttendanceDatabaseFixture()) {
            fx(() -> {
                SessionManager.logout();
                deniedLoad();
                for (String role : List.of("ADMIN", "LECTURER", "STUDENT")) {
                    SessionManager.startSession(new User(1, "fixture", "Fixture", "", role, true));
                    deniedLoad();
                    try {
                        NavigationUtil.navigate(new Label(), "/fxml/attendance.fxml", "Denied");
                        throw new AssertionError("Unauthorized navigation");
                    } catch (SecurityException expected) { checks++; }
                }
                AttendanceDatabaseFixture.signInFixture();
                FXMLLoader loader = new FXMLLoader(AttendanceViewSmokeTest.class.getResource("/fxml/attendance.fxml"));
                loader.setControllerFactory(type -> new AttendanceController(fixture.service()));
                root = loader.load();
                for (var field : AttendanceController.class.getDeclaredFields()) {
                    if (field.isAnnotationPresent(FXML.class)) {
                        field.setAccessible(true);
                        check(field.get(loader.getController()) != null, "FXML injected: " + field.getName());
                    }
                }
                stage = new Stage();
                scene = new Scene(root, 1100, 690);
                stage.setScene(scene);
                stage.setOpacity(0);
                stage.show();
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(combo("offeringCombo").getItems().size() == 3,
                        "active offerings across departments load without officer department/profile");
                ComboBox<AttendanceOffering> offerings = combo("offeringCombo");
                offerings.getItems().stream().filter(row -> row.offeringId() == 10).findFirst().ifPresent(offerings::setValue);
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(combo("enrollmentCombo").getItems().size() == 2, "scoped undergraduate choices loaded");
                ((TextField) root.lookup("#startTimeField")).setText("09:00");
                ((TextField) root.lookup("#endTimeField")).setText("10:00");
                combo("sessionTypeCombo").setValue("THEORY");
                ((TextField) root.lookup("#topicField")).setText("Temporary table UI verification");
                button("Save Session").fire();
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(combo("sessionCombo").getValue() != null, "created session selected");
                combo("enrollmentCombo").getSelectionModel().selectFirst();
                combo("attendanceStatusCombo").setValue("PRESENT");
                button("Save / Update Attendance").fire();
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(table("attendanceTable").getItems().size() == 1, "PRESENT persisted and displayed");
                combo("enrollmentCombo").getSelectionModel().select(1);
                combo("attendanceStatusCombo").setValue("ABSENT");
                button("Save / Update Attendance").fire();
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(table("attendanceTable").getItems().size() == 2, "ABSENT persisted and displayed");
                table("attendanceTable").getSelectionModel().selectFirst();
                combo("attendanceStatusCombo").setValue("LATE");
                button("Save / Update Attendance").fire();
                return null;
            });
            awaitIdle();
            fx(() -> { button("Load Attendance").fire(); return null; });
            awaitIdle();
            fx(() -> {
                TableView<AttendanceRecord> records = table("attendanceTable");
                check(records.getItems().getFirst().getAttendanceStatus().equals("LATE"), "selected row updated and reloaded");
                button("Load Batch Summary").fire();
                return null;
            });
            awaitIdle();
            fx(() -> {
                check(table("summaryTable").getItems().size() == 2, "existing summary workflow loads");
                table("summaryTable").getSelectionModel().selectFirst();
                check(((Label) root.lookup("#summaryDetailsLabel")).getText().contains("Late: 1"), "summary details reflect saved update");
                return null;
            });
            for (int[] size : List.of(new int[]{1100, 720}, new int[]{900, 600}, new int[]{640, 480})) {
                fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                awaitLayout();
                fx(() -> {
                    root.applyCss(); root.layout();
                    ScrollPane scroll = (ScrollPane) root.lookup(".scroll-pane");
                    check(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1,
                            "main content fits " + size[0]);
                    for (String id : List.of("offeringCombo", "sessionCombo", "enrollmentCombo")) {
                        Node selector = root.lookup("#" + id);
                        check(selector.localToScene(selector.getBoundsInLocal()).getMaxX() < scene.getWidth(), "selector fits: " + id);
                    }
                    check(stage.getStyle() == StageStyle.DECORATED && !stage.isFullScreen(), "native title bar");
                    if (List.of(args).contains("--snapshot")) snapshot("attendance-" + size[0]);
                    return null;
                });
            }
            fx(() -> {
                button("Clear").fire();
                check(combo("offeringCombo").getValue() == null && table("attendanceTable").getItems().isEmpty(), "clear resets context");
                var identity = SessionManager.requireSession();
                double width = stage.getWidth(), height = stage.getHeight();
                button("Back to Dashboard").fire();
                check(scene.getRoot().getId().equals("technicalOfficerRoot"), "Back opens Technical Officer dashboard");
                check(SessionManager.isCurrentSession(identity), "Back preserves session");
                check(stage.getWidth() == width && stage.getHeight() == height, "Back preserves window dimensions");
                root = scene.getRoot(); root.applyCss(); root.layout();
                FXMLLoader loader = new FXMLLoader(AttendanceViewSmokeTest.class.getResource("/fxml/attendance.fxml"));
                loader.setControllerFactory(type -> new AttendanceController(fixture.service()));
                scene.setRoot(loader.load());
                root = scene.getRoot();
                return null;
            });
            awaitIdle();
            fx(() -> {
                button("Logout").fire();
                check(!SessionManager.isLoggedIn() && scene.getRoot().lookup("#loginButton") != null, "Attendance Logout returns to login");
                deniedLoad();
                return null;
            });

            if (List.of(args).contains("--live-read")) {
                var officer = new UserDAO().getAllUsers().stream()
                        .filter(user -> "tec".equals(user.getUsername())
                                && user.isActive() && "TECHNICAL_OFFICER".equals(user.getRole()))
                        .findFirst().orElseThrow();
                for (String action : List.of("Attendance Management", "Manage Attendance")) {
                    fx(() -> {
                        SessionManager.startSession(officer);
                        NavigationUtil.navigate(scene.getRoot(), NavigationUtil.dashboardResource(), "FTMS");
                        root = scene.getRoot(); root.applyCss(); root.layout();
                        button(action).fire();
                        root = scene.getRoot();
                        check(root.getId().equals("attendanceRoot"), "real dashboard action opens Attendance: " + action);
                        return null;
                    });
                    awaitIdle();
                    fx(() -> {
                        check(!combo("offeringCombo").getItems().isEmpty(), "tec active course offerings loaded");
                        check(!((Label) root.lookup("#statusLabel")).getText().contains("department"),
                                "no department warning for tec");
                        var identity = SessionManager.requireSession();
                        button("Back to Dashboard").fire();
                        check(SessionManager.isCurrentSession(identity), "live Back preserves identity");
                        return null;
                    });
                }
                System.out.println("tec account session: Attendance unlocked and active offerings loaded from both dashboard buttons. Credential login was not tested.");
            }
            System.out.println("Attendance FXML/JDBC UI: " + checks + " checks passed. All writes were temporary-table fixtures.");
        } finally {
            fx(() -> { SessionManager.logout(); if (stage != null) stage.close(); return null; });
            Platform.exit();
        }
    }

    @SuppressWarnings("unchecked") private static <T> ComboBox<T> combo(String id) { return (ComboBox<T>) root.lookup("#" + id); }
    @SuppressWarnings("unchecked") private static <T> TableView<T> table(String id) { return (TableView<T>) root.lookup("#" + id); }
    private static Button button(String label) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
    }
    private static void deniedLoad() throws Exception {
        try {
            new FXMLLoader(AttendanceViewSmokeTest.class.getResource("/fxml/attendance.fxml")).load();
            throw new AssertionError("Unauthorized FXML loaded");
        } catch (javafx.fxml.LoadException exception) {
            Throwable cause = exception;
            while (cause != null && !(cause instanceof SecurityException)) cause = cause.getCause();
            check(cause instanceof SecurityException, "direct FXML load denied");
        }
    }
    private static void awaitIdle() throws Exception {
        awaitLoadedOrDenied();
        fx(() -> {
            check(!((VBox) root.lookup("#workspace")).isDisabled(),
                    "workspace ready: " + ((Label) root.lookup("#statusLabel")).getText());
            return null;
        });
    }
    private static void awaitLoadedOrDenied() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                String text = ((Label) root.lookup("#statusLabel")).getText();
                return !text.startsWith("Loading") && !text.startsWith("Saving");
            })) return;
            settle();
        }
        throw new AssertionError("Attendance background task timed out");
    }
    private static void settle() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        fx(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(150));
            pause.setOnFinished(event -> done.countDown()); pause.play(); return null;
        });
        if (!done.await(5, TimeUnit.SECONDS)) throw new AssertionError("JavaFX pulse timed out");
    }
    private static void awaitLayout() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            settle(); // Native Windows resize and fitToWidth may need separate pulses.
            if (fx(() -> {
                root.applyCss(); root.layout();
                ScrollPane scroll = (ScrollPane) root.lookup(".scroll-pane");
                return Math.abs(root.getLayoutBounds().getWidth() - scene.getWidth()) < 1
                        && stage.getWidth() - scene.getWidth() < 40
                        && scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1
                        && root.lookup("#offeringCombo").localToScene(root.lookup("#offeringCombo").getBoundsInLocal())
                            .getMaxX() < scene.getWidth();
            })) return;
        } while (System.nanoTime() < deadline);
        throw new AssertionError(fx(() -> "Layout did not settle: stage=" + stage.getWidth()
                + ", scene=" + scene.getWidth() + ", root=" + root.getLayoutBounds().getWidth()
                + ", selectorRight=" + root.lookup("#offeringCombo")
                    .localToScene(root.lookup("#offeringCombo").getBoundsInLocal()).getMaxX()));
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
    private static void snapshot(String name) throws Exception {
        var image = root.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Path directory = Path.of("target", "attendance-ui-check");
        Files.createDirectories(directory);
        ImageIO.write(buffered, "png", directory.resolve(name + ".png").toFile());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
