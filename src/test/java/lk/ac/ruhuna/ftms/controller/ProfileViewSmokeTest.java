package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import lk.ac.ruhuna.ftms.dao.ProfileDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.ProfileService;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;
import lk.ac.ruhuna.ftms.util.ProfilePictureStore;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Loads real FXML and exercises asynchronous controllers without a window or database. */
public final class ProfileViewSmokeTest {
    private static Parent root;
    private static Scene scene;
    private static ProfileController controller;
    private static int checks;

    public static void main(String[] args) throws Exception {
        java.nio.file.Path pictureRoot = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "profile-view-pictures-");
        java.nio.file.Path sourcePicture = pictureRoot.resolve("source.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 24,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", sourcePicture.toFile());
        ProfilePictureStore pictureStore = new ProfilePictureStore(pictureRoot.resolve("managed"));
        Platform.startup(() -> {});
        try {
            onFx(() -> {
                SessionManager.logout();
                try {
                    new FXMLLoader(ProfileViewSmokeTest.class.getResource("/fxml/profile.fxml")).load();
                    throw new AssertionError("Profile opened without a session");
                } catch (java.io.IOException expected) {
                    check(hasCause(expected, SecurityException.class), "FXML rejects anonymous access");
                }
                return null;
            });

            for (String role : new String[]{"ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"}) {
                MemoryDAO dao = new MemoryDAO(role);
                onFx(() -> {
                    SessionManager.startSession(dao.copy());
                    FXMLLoader loader = new FXMLLoader(ProfileViewSmokeTest.class.getResource("/fxml/profile.fxml"));
                    loader.setControllerFactory(type -> new ProfileController(new ProfileService(dao, pictureStore)));
                    root = loader.load();
                    controller = loader.getController();
                    scene = new Scene(root, 1200, 760);
                    root.applyCss();
                    root.layout();
                    for (var field : ProfileController.class.getDeclaredFields()) {
                        if (field.isAnnotationPresent(FXML.class)) {
                            check(loader.getNamespace().get(field.getName()) != null, "fx:id " + field.getName());
                        }
                    }
                    check(button("saveButton").isDisabled(), "save disabled during load");
                    check(button("refreshButton").isDisabled(), "refresh disabled during load");
                    check(!field("usernameField").isEditable(), "username is read-only");
                    check(!field("roleField").isEditable(), "role is read-only");
                    check(root.lookupAll(".password-field").stream().noneMatch(PasswordField.class::isInstance),
                            "no password control for " + role);
                    return null;
                });
                dao.loadGate.countDown();
                await(() -> !button("saveButton").isDisabled());
                onFx(() -> {
                    check(field("roleField").getText().equals(role), "role loaded");
                    if (java.util.Arrays.asList(args).contains("--snapshot") && role.equals("LECTURER")) {
                        snapshot("target/profile-wide.png");
                    }
                    root.resize(900, 600);
                    root.layout();
                    root.layout();
                    check(GridPane.getRowIndex(root.lookup("#detailsCard")) == 1, "cards stack at narrow width");
                    if (java.util.Arrays.asList(args).contains("--snapshot") && role.equals("LECTURER")) {
                        snapshot("target/profile-narrow.png");
                    }
                    root.resize(1400, 900);
                    root.layout();
                    root.layout();
                    check(GridPane.getColumnIndex(root.lookup("#detailsCard")) == 1, "cards sit side by side at wide width");
                    field("fullNameField").setText("Unsaved Name");
                    button("resetButton").fire();
                    check(field("fullNameField").getText().equals("Original Name"), "reset restores loaded values");
                    check(root.lookup("#defaultAvatar").isVisible(), "no picture shows default avatar");
                    check(button("removePictureButton").isDisabled(), "no picture cannot be removed");
                    field("fullNameField").setText("Unsaved picture edit");
                    controller.uploadPicture(sourcePicture);
                    check(button("changePictureButton").isDisabled(), "upload controls disabled while saving");
                    return null;
                });
                await(() -> !button("changePictureButton").isDisabled());
                onFx(() -> {
                    check(((javafx.scene.image.ImageView) root.lookup("#profileImage")).getImage() != null, "uploaded image refreshed");
                    check(!root.lookup("#defaultAvatar").isVisible(), "picture replaces default avatar");
                    check(!button("removePictureButton").isDisabled(), "saved picture can be removed");
                    check(field("fullNameField").getText().equals("Unsaved picture edit"), "picture save preserves unsaved details");
                    button("removePictureButton").fire();
                    return null;
                });
                await(() -> !button("changePictureButton").isDisabled());
                onFx(() -> {
                    check(root.lookup("#defaultAvatar").isVisible(), "removal restores default avatar");
                    check(((javafx.scene.image.ImageView) root.lookup("#profileImage")).getImage() == null, "removed image cleared");
                    check(dao.picture == null, "remove persists null");
                    button("resetButton").fire();
                    field("emailField").setText("invalid");
                    button("saveButton").fire();
                    return null;
                });
                await(() -> !button("saveButton").isDisabled());
                onFx(() -> {
                    check(label("statusLabel").getStyleClass().contains("status-error"), "validation error shown");
                    field("fullNameField").setText("Updated Person");
                    field("emailField").setText("updated@example.com");
                    button("saveButton").fire();
                    check(button("dashboardButton").isDisabled(), "navigation waits for save");
                    check(button("logoutButton").isDisabled(), "logout waits for save");
                    return null;
                });
                check(dao.saveEntered.await(10, TimeUnit.SECONDS), "save worker started");
                onFx(() -> { check(button("saveButton").isDisabled(), "FX thread responds during save"); return null; });
                dao.saveGate.countDown();
                await(() -> !button("saveButton").isDisabled());
                onFx(() -> {
                    check(label("statusLabel").getText().contains("saved successfully"), "success feedback");
                    check(label("summaryNameLabel").getText().equals("Updated Person"), "summary refreshed");
                    check(SessionManager.getCurrentUser().getFullName().equals("Updated Person"), "session refreshed");
                    dao.failSave = new SQLException("Synthetic duplicate", "23000", 1062);
                    field("emailField").setText("duplicate@example.com");
                    button("saveButton").fire();
                    return null;
                });
                await(() -> !button("saveButton").isDisabled());
                onFx(() -> {
                    check(label("statusLabel").getText().contains("already in use"), "duplicate email feedback");
                    check(field("emailField").getText().equals("duplicate@example.com"), "failed save retains edits");
                    check(SessionManager.getCurrentUser().getEmail().equals("updated@example.com"), "failed save preserves session");
                    dao.failSave = null;
                    dao.failRead = true;
                    button("refreshButton").fire();
                    return null;
                });
                await(() -> !button("refreshButton").isDisabled());
                onFx(() -> {
                    check(button("saveButton").isDisabled(), "failed refresh disables editing");
                    check(label("statusLabel").getText().contains("database connection"), "connection error feedback");
                    dao.failRead = false;
                    button("refreshButton").fire();
                    return null;
                });
                await(() -> !button("saveButton").isDisabled());
                onFx(() -> {
                    button("dashboardButton").fire();
                    root = scene.getRoot();
                    root.applyCss();
                    root.layout();
                    check(label(role.equals("LECTURER") ? "welcomeLabel" : "nameLabel").getText().contains("Updated Person"), "return to dashboard uses new name");
                    check(root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).anyMatch(b -> b.getText().contains("Profile")), "dashboard exposes My Profile");
                    Button logout = root.lookupAll(".button").stream().filter(Button.class::isInstance)
                            .map(Button.class::cast).filter(button -> button.getText().replace(" ", "").equalsIgnoreCase("LOGOUT")).findFirst().orElseThrow();
                    logout.fire();
                    root = scene.getRoot();
                    check(root.lookup("#loginButton") != null, "dashboard logout opens login FXML");
                    check(!SessionManager.isLoggedIn(), "logout clears session");
                    return null;
                });
            }
            System.out.println("Profile FXML/controller checks passed: " + checks + " (all four roles).");
        } finally {
            SessionManager.logout();
            Platform.exit();
            try (var paths = java.nio.file.Files.walk(pictureRoot)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    java.nio.file.Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Button button(String id) { return (Button) root.lookup("#" + id); }
    private static void snapshot(String path) throws java.io.IOException {
        javafx.scene.image.WritableImage image = root.snapshot(null, null);
        java.awt.image.BufferedImage png = new java.awt.image.BufferedImage((int) image.getWidth(),
                (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        javax.imageio.ImageIO.write(png, "png", new java.io.File(path));
    }
    private static Label label(String id) { return (Label) root.lookup("#" + id); }
    private static TextField field(String id) { return (TextField) root.lookup("#" + id); }
    private static boolean hasCause(Throwable error, Class<?> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) { return true; }
        }
        return false;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!onFx(condition)) {
            if (System.nanoTime() > deadline) { throw new AssertionError("UI operation timed out"); }
            Thread.sleep(20);
        }
    }

    private static final class MemoryDAO extends ProfileDAO {
        private final User profile;
        private final CountDownLatch loadGate = new CountDownLatch(1);
        private final CountDownLatch saveGate = new CountDownLatch(1);
        private final CountDownLatch saveEntered = new CountDownLatch(1);
        private volatile SQLException failSave;
        private volatile boolean failRead;
        private volatile String picture;

        MemoryDAO(String role) { profile = new User(7, "owner", "Original Name", "owner@example.com", role, true); }
        User copy() {
            User user = new User(7, "owner", profile.getFullName(), profile.getEmail(), profile.getRole(), true);
            user.setProfilePicture(picture);
            return user;
        }

        @Override public User getProfile(SessionIdentity identity) throws SQLException {
            if (Platform.isFxApplicationThread()) { throw new AssertionError("Read on FX thread"); }
            waitFor(loadGate);
            if (failRead) { throw new SQLException("Synthetic connection failure", "08001"); }
            return copy();
        }
        @Override public boolean updateProfile(SessionIdentity identity, String name, String email) throws SQLException {
            if (Platform.isFxApplicationThread()) { throw new AssertionError("Write on FX thread"); }
            saveEntered.countDown();
            waitFor(saveGate);
            if (failSave != null) { throw failSave; }
            profile.setFullName(name);
            profile.setEmail(email);
            return true;
        }
        @Override public boolean updateProfilePicture(SessionIdentity identity, String expected, String filename) {
            if (Platform.isFxApplicationThread()) { throw new AssertionError("Picture write on FX thread"); }
            SessionManager.requireSession(identity);
            if (!java.util.Objects.equals(expected, picture)) { return false; }
            picture = filename;
            return true;
        }
        private static void waitFor(CountDownLatch gate) throws SQLException {
            try {
                if (!gate.await(15, TimeUnit.SECONDS)) { throw new SQLException("Timed out waiting for test"); }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new SQLException("Test interrupted", error);
            }
        }
    }
}
