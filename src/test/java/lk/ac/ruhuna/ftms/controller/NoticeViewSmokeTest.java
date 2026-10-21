package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.stage.Window;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.Notice.Audience;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.service.NoticeService;
import lk.ac.ruhuna.ftms.service.NoticeServiceTest;
import lk.ac.ruhuna.ftms.service.NoticeServiceTest.MemoryDAO;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Explicit JavaFX runner. Default uses a memory DAO; --live-navigation also opens
 * real DAO-backed notice screens through every sidebar/card, with no SQL writes.
 */
public final class NoticeViewSmokeTest {
    private static Scene scene;
    private static Stage stage;
    private static int checks;
    private static final MemoryDAO dao = new MemoryDAO();
    private static List<User> liveUsers = List.of();
    private static final Map<String, String> ROOTS = Map.of("ADMIN", "adminRoot", "LECTURER", "lecturerRoot",
            "STUDENT", "studentRoot", "TECHNICAL_OFFICER", "technicalOfficerRoot");

    public static void main(String[] args) throws Exception {
        if (List.of(args).contains("--live-navigation")) liveUsers = new UserDAO().getAllUsers();
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        check(ready.await(10, TimeUnit.SECONDS), "JavaFX starts");
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                login("ADMIN");
                stage = new Stage(); stage.setOpacity(0);
                scene = new Scene(load("notices.fxml"), 1100, 700);
                stage.setScene(scene); stage.show();
                check(table().getItems().isEmpty(), "admin empty state");
                check(button("Edit Selected").isDisabled() && button("Delete Notice").isDisabled(), "selection actions disabled");
                check(audience().getItems().size() == 5 && audience().getValue() == Audience.ALL, "all database audience choices available");
                button("Save Notice").fire();
                check(dao.rows.isEmpty() && label("statusLabel").getText().contains("title"), "invalid form does not save");
                text("titleField").setText("Draft title"); area("contentField").setText("Draft full content");
                audience().setValue(Audience.STUDENTS);
                button("Save Notice").fire();
                check(dao.rows.size() == 1 && !dao.rows.getFirst().isActive(), "admin creates draft");
                check(dao.rows.getFirst().getAudience() == Audience.STUDENTS, "audience persisted from create form");
                table().getSelectionModel().selectFirst();
                long id = table().getSelectionModel().getSelectedItem().getNoticeId();
                check(text("titleField").getText().equals("Draft title"), "selection populates title");
                check(area("contentField").getText().equals("Draft full content"), "selection populates full content");
                check(audience().getValue() == Audience.STUDENTS && !text("publishedAtField").getText().isBlank(), "selection populates audience and start");
                String originalStart = text("publishedAtField").getText();
                text("publishedAtField").setText("2026-02-30 10:00:00"); button("Save Changes").fire();
                check(label("statusLabel").getText().contains("must use"), "invalid calendar date rejected");
                text("publishedAtField").setText(originalStart);
                button("Edit Selected").fire();
                audience().setValue(Audience.ALL);
                text("titleField").setText("Updated title"); area("contentField").setText("Updated content\nSecond line");
                button("Save Changes").fire();
                check(dao.rows.size() == 1 && dao.rows.getFirst().getNoticeId() == id, "edit preserves notice ID");
                check(dao.rows.getFirst().getContent().contains("Second line"), "edit persists content");
                check(dao.rows.getFirst().getAudience() == Audience.ALL, "edit changes audience");
                check(dao.rows.getFirst().getPublishedAt().format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))
                        .equals(originalStart), "edit preserves start timestamp");
                table().getSelectionModel().selectFirst(); button("Publish").fire();
                check(dao.rows.getFirst().isActive(), "publish button changes state");
                table().getSelectionModel().selectFirst(); button("Unpublish").fire();
                check(!dao.rows.getFirst().isActive(), "unpublish button changes state");
                text("searchField").setText("Second line"); button("Search").fire();
                check(table().getItems().size() == 1, "content search");
                table().getSelectionModel().selectFirst();
                text("searchField").setText("missing"); button("Search").fire();
                check(table().getItems().isEmpty() && text("titleField").getText().isEmpty(), "search clears stale edit form");
                button("Show All / Refresh").fire();
                check(table().getItems().size() == 1, "show all refreshes");
                table().getSelectionModel().selectFirst(); confirmDelete(ButtonType.CANCEL);
                check(dao.rows.size() == 1, "delete cancellation preserves row");
                confirmDelete(ButtonType.OK);
                check(dao.rows.isEmpty() && table().getItems().isEmpty(), "confirmed delete refreshes table");
                text("titleField").setText("Published notice"); area("contentField").setText("Complete content\n" + "More text. ".repeat(100));
                ((CheckBox) scene.getRoot().lookup("#publishedCheckBox")).setSelected(true);
                button("Save Notice").fire();
                check(dao.rows.getFirst().isActive(), "save as published checkbox");
                table().getSelectionModel().selectFirst();
                text("publishedAtField").setText("2030-01-01 10:00:00");
                text("expiresAtField").setText("2030-02-01 10:00:00");
                button("Save Changes").fire();
                table().getSelectionModel().selectFirst();
                check(text("publishedAtField").getText().equals("2030-01-01 10:00:00")
                        && text("expiresAtField").getText().equals("2030-02-01 10:00:00"), "schedule and expiry round-trip through form");
                text("publishedAtField").setText(originalStart); text("expiresAtField").clear(); button("Save Changes").fire();
                assertLayout("admin", List.of(args).contains("--snapshot"));
                var identity = SessionManager.requireSession();
                button("Back to Admin Dashboard").fire();
                check("adminRoot".equals(scene.getRoot().getId()) && SessionManager.isCurrentSession(identity), "admin Back preserves session");
                return null;
            });

            for (String role : NoticeServiceTest.ROLES) fx(() -> {
                login(role);
                var identity = SessionManager.requireSession();
                dao.rows.getFirst().setActive(true);
                scene.setRoot(load("view_notices.fxml"));
                check(table().getItems().size() == 1, role + " viewer only published rows");
                check(!area("contentArea").isEditable() && !table().isEditable(), role + " viewer read only");
                check(scene.getRoot().lookup("#saveButton") == null && scene.getRoot().lookup("#deleteButton") == null,
                        role + " no management controls");
                check(table().getColumns().get(1).getText().equals("Published from"), "honest date label");
                table().getSelectionModel().selectFirst();
                check(area("contentArea").getText().equals(dao.rows.getFirst().getContent()), "full selected content");
                dao.rows.getFirst().setContent("Content edited by admin");
                button("Refresh Notices").fire();
                check(area("contentArea").getText().equals("Content edited by admin"), "refresh retains selection with new content");
                dao.rows.getFirst().setAudience(role.equals("ADMIN") ? Audience.STUDENTS : Audience.ADMINS);
                button("Refresh Notices").fire();
                check(table().getItems().isEmpty() && area("contentArea").getText().isEmpty(), "audience change clears inaccessible notice");
                dao.rows.getFirst().setAudience(Audience.ALL); button("Refresh Notices").fire();
                dao.rows.getFirst().setPublishedAt(dao.now.plusDays(1)); button("Refresh Notices").fire();
                check(table().getItems().isEmpty(), "future publication hidden by viewer");
                dao.rows.getFirst().setPublishedAt(dao.now); dao.rows.getFirst().setExpiresAt(dao.now); button("Refresh Notices").fire();
                check(table().getItems().isEmpty(), "expired notice hidden by viewer");
                dao.rows.getFirst().setExpiresAt(null); button("Refresh Notices").fire(); table().getSelectionModel().selectFirst();
                dao.rows.getFirst().setActive(false);
                button("Refresh Notices").fire();
                check(table().getItems().isEmpty() && area("contentArea").getText().isEmpty(), "unpublished notice and stale content disappear");
                check(label("statusLabel").getText().contains("No published notices"), "useful empty state");
                dao.rows.getFirst().setActive(true);
                button("Refresh Notices").fire(); table().getSelectionModel().selectFirst();
                dao.failReads = true; button("Refresh Notices").fire(); dao.failReads = false;
                check(table().getItems().isEmpty() && area("contentArea").getText().isEmpty(), "SQL failure clears stale data");
                check(label("statusLabel").getText().contains("Unable to load"), "SQL failure distinct from empty state");
                button("Refresh Notices").fire(); table().getSelectionModel().selectFirst();
                if (role.equals("STUDENT")) assertLayout("viewer", List.of(args).contains("--snapshot"));
                button("Back to Dashboard").fire();
                check(ROOTS.get(role).equals(scene.getRoot().getId()), role + " correct Back destination");
                check(SessionManager.isCurrentSession(identity), role + " exact session preserved");
                logoutButton().fire();
                check(!SessionManager.isLoggedIn() && scene.getRoot().lookup("#loginButton") != null, role + " logout opens login");
                return null;
            });

            fx(() -> {
                for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER", "UNKNOWN")) {
                    login(role);
                    deniedLoad("notices.fxml");
                    try {
                        NavigationUtil.navigate(scene.getRoot(), "/fxml/notices.fxml", "Forbidden");
                        throw new AssertionError("Non-admin management route allowed");
                    } catch (SecurityException expected) { checks++; }
                    if (role.equals("UNKNOWN")) deniedLoad("view_notices.fxml");
                }
                SessionManager.logout(); deniedLoad("notices.fxml"); deniedLoad("view_notices.fxml");
                login("ADMIN"); scene.setRoot(load("notices.fxml"));
                int calls = dao.calls;
                login("STUDENT"); button("Save Notice").fire();
                check(dao.calls == calls && table().getItems().isEmpty(), "stale admin controller cannot save or retain drafts");
                scene.setRoot(load("view_notices.fxml")); table().getSelectionModel().selectFirst();
                SessionManager.logout(); button("Refresh Notices").fire();
                check(table().getItems().isEmpty() && area("contentArea").getText().isEmpty(), "logged-out viewer clears data");
                return null;
            });

            if (List.of(args).contains("--live-navigation")) for (String role : NoticeServiceTest.ROLES) {
                fx(() -> {
                    login(role);
                    NavigationUtil.navigate(scene.getRoot(), NavigationUtil.dashboardResource(), "FTMS");
                    var identity = SessionManager.requireSession();
                    for (int index = 0; index < 2; index++) {
                        scene.getRoot().applyCss(); scene.getRoot().layout();
                        List<Button> buttons = scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance)
                                .map(Button.class::cast).filter(b -> b.getText().contains("Notices")).toList();
                        check(buttons.size() == 2, role + " has sidebar and card Notices actions");
                        buttons.get(index).fire();
                        check((role.equals("ADMIN") ? "noticesRoot" : "viewNoticesRoot").equals(scene.getRoot().getId()), role + " notice action navigates");
                        if (List.of(args).contains("--live-navigation")) {
                            check(!label("statusLabel").getText().startsWith("Could not")
                                    && !label("statusLabel").getText().startsWith("Unable"), role + " real notice query succeeded");
                            java.util.Set<String> expected = switch (role) {
                                case "ADMIN" -> java.util.Set.of("Welcome to FTMS", "OOP Assignment 01", "Attendance Update", "Results Processing");
                                case "STUDENT" -> java.util.Set.of("Welcome to FTMS", "OOP Assignment 01");
                                case "TECHNICAL_OFFICER" -> java.util.Set.of("Welcome to FTMS", "Attendance Update");
                                default -> java.util.Set.of("Welcome to FTMS");
                            };
                            if (java.time.LocalDate.now().equals(java.time.LocalDate.of(2026, 10, 18))) {
                                check(table().getItems().stream().map(Notice::getTitle).collect(java.util.stream.Collectors.toSet()).equals(expected),
                                        role + " dashboard action shows exact expected records");
                            }
                        }
                        button(role.equals("ADMIN") ? "Back to Admin Dashboard" : "Back to Dashboard").fire();
                        check(ROOTS.get(role).equals(scene.getRoot().getId()) && SessionManager.isCurrentSession(identity), role + " sidebar/card round trip");
                    }
                    // Existing lecturer/TO allowlists still reject unrelated management destinations.
                    if (role.equals("LECTURER") || role.equals("TECHNICAL_OFFICER")) {
                        for (String resource : List.of("/fxml/Users.fxml", "/fxml/admin_dashboard.fxml")) {
                            try { NavigationUtil.navigate(scene.getRoot(), resource, "Forbidden"); throw new AssertionError("Allowlist bypass"); }
                            catch (SecurityException expected) { checks++; }
                        }
                    }
                    return null;
                });
            }
            System.out.println("Notice JavaFX checks passed: " + checks);
        } finally {
            fx(() -> { for (Window window : new ArrayList<>(Window.getWindows())) window.hide(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static Parent load(String resource) throws Exception {
        FXMLLoader loader = new FXMLLoader(NoticeViewSmokeTest.class.getResource("/fxml/" + resource));
        loader.setControllerFactory(type -> {
            NoticeService service = new NoticeService(dao);
            if (type == NoticeController.class) return new NoticeController(service);
            if (type == ViewNoticesController.class) return new ViewNoticesController(service);
            throw new AssertionError(type);
        });
        Parent root = loader.load();
        for (var field : loader.getController().getClass().getDeclaredFields()) if (field.isAnnotationPresent(FXML.class)) {
            field.setAccessible(true); check(field.get(loader.getController()) != null, "FXML injection: " + field.getName());
        }
        return root;
    }
    private static void deniedLoad(String resource) throws Exception {
        try { new FXMLLoader(NoticeViewSmokeTest.class.getResource("/fxml/" + resource)).load(); }
        catch (Exception error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) if (cause instanceof SecurityException) { checks++; return; }
            throw error;
        }
        throw new AssertionError("Direct load allowed: " + resource);
    }
    private static void confirmDelete(ButtonType result) {
        Platform.runLater(() -> {
            for (Window window : new ArrayList<>(Window.getWindows())) if (window.getScene().getRoot() instanceof DialogPane dialog) {
                ((Button) dialog.lookupButton(result)).fire(); return;
            }
            throw new AssertionError("Missing delete confirmation");
        });
        button("Delete Notice").fire();
    }
    private static void assertLayout(String name, boolean snapshot) throws Exception {
        Parent root = scene.getRoot();
        for (int[] size : List.of(new int[]{1100, 700}, new int[]{900, 600}, new int[]{800, 600})) {
            root.resize(size[0], size[1]); root.applyCss(); root.layout();
            ScrollPane scroll = (ScrollPane) root.lookup(".scroll-pane");
            check(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1, "content fits " + size[0]);
            for (var node : root.lookupAll(".button")) if (node instanceof Button button) {
                check(button.getBoundsInParent().getMaxX() <= button.getParent().getLayoutBounds().getWidth() + 1, "button fits horizontally");
            }
            if (snapshot && size[0] == 900) {
                var image = root.snapshot(null, null);
                int width = (int) image.getWidth(), height = (int) image.getHeight();
                int[] pixels = new int[width * height];
                image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                output.setRGB(0, 0, width, height, pixels, 0, width);
                ImageIO.write(output, "png", Path.of("target/notices-" + name + ".png").toFile());
            }
            scroll.setVvalue(1); root.layout();
            var bottomControl = root.lookup(name.equals("admin") ? "#deleteButton" : "#contentArea");
            check(bottomControl.localToScene(bottomControl.getBoundsInLocal()).getMaxY() <= root.getLayoutBounds().getHeight() + 1,
                    "bottom control reachable by scrolling at " + size[0]);
            scroll.setVvalue(0); root.layout();
        }
    }
    private static void login(String role) {
        SessionManager.startSession(liveUsers.stream().filter(u -> u.isActive() && role.equals(u.getRole())).findFirst()
                .orElseGet(() -> NoticeServiceTest.user(role)));
    }
    @SuppressWarnings("unchecked") private static ComboBox<Audience> audience() { return (ComboBox<Audience>) scene.getRoot().lookup("#audienceCombo"); }
    @SuppressWarnings("unchecked") private static TableView<Notice> table() {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return (TableView<Notice>) scene.getRoot().lookup("#noticeTable");
    }
    private static TextField text(String id) { return (TextField) scene.getRoot().lookup("#" + id); }
    private static TextArea area(String id) { return (TextArea) scene.getRoot().lookup("#" + id); }
    private static Label label(String id) {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return (Label) scene.getRoot().lookup("#" + id);
    }
    private static Button button(String text) {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
    private static Button logoutButton() {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getText().toLowerCase().replace(" ", "").contains("logout")).findFirst().orElseThrow();
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
