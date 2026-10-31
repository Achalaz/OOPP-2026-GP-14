package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.AcademicDatabaseFixture;
import lk.ac.ruhuna.ftms.service.AuthService;
import lk.ac.ruhuna.ftms.service.UserService;
import lk.ac.ruhuna.ftms.util.PasswordUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Real User Management FXML, transaction and login; writes only connection-local temporary tables. */
public final class UserPasswordViewSmokeTest {
    private record Feedback(boolean error, String message) { }
    private static final List<Feedback> feedback = new ArrayList<>();
    private static Scene scene;
    private static int checks;

    public static void main(String[] args) throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX started");
        try (var db = new AcademicDatabaseFixture()) {
            db.login(5, "ADMIN");
            db.execute("UPDATE users SET username='Lec2',is_active=FALSE WHERE user_id=2");
            var service = new UserService();
            var dao = new UserDAO();
            var auth = new AuthService();
            for (int id : new int[]{1, 2}) {
                var user = dao.findUserById(id);
                user.setEmail("fixture" + id + "@example.edu");
                service.updateUser(user, "Old-fixture-password!");
            }
            String originalHash = dao.findByUsername("academic-test-1").orElseThrow().passwordHash();
            fx(() -> {
                var loader = new FXMLLoader(UserPasswordViewSmokeTest.class.getResource("/fxml/Users.fxml"));
                loader.setControllerFactory(type -> new UsersController() {
                    @Override void showError(String message) { feedback.add(new Feedback(true, message)); }
                    @Override void showInfo(String message) { feedback.add(new Feedback(false, message)); }
                });
                scene = new Scene(loader.load(), 1100, 750);
                scene.getRoot().applyCss(); scene.getRoot().layout();
                check(feedback.isEmpty(), "User Management loads");
                password().setText("Unselected-fixture-password!");
                check(update().error(), "selection required");
                check(password().getText().isEmpty(), "missing selection clears password");
                select(1);
                field("nameField").setText("Details Only");
                check(!update().error(), "blank password saves details");
                check(feedback.getLast().message().contains("Password and account activation are unchanged"), "details-only success explains unchanged credentials");
                check(password().getText().isEmpty(), "details-only save clears password");
                select(1);
                field("nameField").setText("Must Not Be Saved");
                password().setText("short");
                check(update().error(), "short password rejected");
                check(feedback.getLast().message().contains("at least 8 characters"), "password validation explained");
                check(password().getText().isEmpty(), "invalid password cleared");
                check(table().getSelectionModel().getSelectedItem().getFullName().equals("Details Only"), "failed update does not mutate selected table row");
                return null;
            });
            check(originalHash.equals(dao.findByUsername("academic-test-1").orElseThrow().passwordHash()), "blank and rejected updates preserve hash");
            check(auth.authenticate("academic-test-1", "Old-fixture-password!".toCharArray()).isPresent(), "old login still works");
            fx(() -> {
                field("nameField").setText("Password Updated");
                password().setText("New-fixture-password!");
                check(!update().error(), "valid new password saves");
                check(feedback.getLast().message().contains("details and password updated"), "password success is explicit");
                check(password().getText().isEmpty(), "successful password update clears input");
                return null;
            });
            check(auth.authenticate("academic-test-1", "New-fixture-password!".toCharArray()).isPresent(), "new login succeeds through AuthService");
            check(auth.authenticate("academic-test-1", "Old-fixture-password!".toCharArray()).isEmpty(), "old password no longer authenticates");
            db.failAfterWrite("SET password_hash");
            fx(() -> {
                select(1);
                field("nameField").setText("Rollback Name");
                password().setText("Rejected-fixture-password!");
                var notice = update();
                check(notice.error() && notice.message().equals("Unable to save the user update. Refresh and try again."), "database failure uses safe error message");
                check(password().getText().isEmpty(), "database failure clears password");
                check(table().getSelectionModel().getSelectedItem().getFullName().equals("Password Updated"), "database failure leaves table row unchanged");
                return null;
            });
            check(dao.findUserById(1).getFullName().equals("Password Updated"), "password failure rolls back details");
            check(auth.authenticate("academic-test-1", "New-fixture-password!".toCharArray()).isPresent(), "password failure preserves current login");
            fx(() -> {
                select(2);
                check(password().getText().isEmpty(), "selecting user never displays hash");
                password().setText("Inactive-fixture-password!");
                check(!update().error(), "inactive account password can be reset");
                check(password().getText().isEmpty(), "inactive account reset clears password");
                return null;
            });
            var inactive = dao.findByUsername("Lec2").orElseThrow();
            check(!inactive.user().isActive(), "Lec2 remains inactive");
            check(PasswordUtil.verifyPassword("Inactive-fixture-password!".toCharArray(), inactive.passwordHash()), "inactive account receives requested hash");
            check(auth.authenticate("Lec2", "Inactive-fixture-password!".toCharArray()).isEmpty(), "inactive account cannot log in after password reset");
            System.out.println("User password JavaFX/MySQL checks passed: " + checks + "; temporary tables only.");
        } finally { Platform.exit(); }
    }

    private static Feedback update() {
        var originalOut = System.out;
        var originalErr = System.err;
        var captured = new ByteArrayOutputStream();
        int previous = feedback.size();
        try (var sink = new PrintStream(captured)) {
            System.setOut(sink); System.setErr(sink);
            scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("Update User")).findFirst().orElseThrow().fire();
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        check(captured.size() == 0, "user update must not log credential data or database errors");
        check(feedback.size() == previous + 1, "one clear operation result shown");
        return feedback.getLast();
    }
    private static void select(int id) {
        table().getSelectionModel().select(table().getItems().stream().filter(user -> user.getUserId() == id).findFirst().orElseThrow());
    }
    @SuppressWarnings("unchecked") private static TableView<User> table() { return (TableView<User>) scene.getRoot().lookup("#usersTable"); }
    private static TextField field(String id) { return (TextField) scene.getRoot().lookup("#" + id); }
    private static PasswordField password() { return (PasswordField) field("passwordField"); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
