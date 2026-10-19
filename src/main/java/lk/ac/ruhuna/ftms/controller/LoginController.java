
package lk.ac.ruhuna.ftms.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.AuthService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class LoginController {

    @FXML private StackPane loginRoot;
    @FXML private ImageView backgroundImage;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private ImageView eyeIcon;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private final AuthService authService =
            new AuthService();

    private Image visibleIcon;
    private Image hiddenIcon;
    private boolean passwordVisible = false;

    @FXML
    public void initialize() {

        backgroundImage.fitWidthProperty()
                .bind(loginRoot.widthProperty());

        backgroundImage.fitHeightProperty()
                .bind(loginRoot.heightProperty());

        visibleIcon = loadImage("/images/visible.png");
        hiddenIcon = loadImage("/images/hidden.png");

        visiblePasswordField.textProperty()
                .bindBidirectional(
                        passwordField.textProperty()
                );

        passwordField.setVisible(true);
        visiblePasswordField.setVisible(false);

        passwordField.managedProperty()
                .bind(passwordField.visibleProperty());

        visiblePasswordField.managedProperty()
                .bind(visiblePasswordField.visibleProperty());

        eyeIcon.setImage(visibleIcon);
    }

    private Image loadImage(String path) {
        return new Image(
                Objects.requireNonNull(
                        getClass().getResource(path),
                        "Image not found: " + path
                ).toExternalForm()
        );
    }

    @FXML
    private void togglePasswordVisibility() {

        passwordVisible = !passwordVisible;

        passwordField.setVisible(!passwordVisible);
        visiblePasswordField.setVisible(passwordVisible);

        eyeIcon.setImage(
                passwordVisible ? hiddenIcon : visibleIcon
        );

        TextField field = passwordVisible
                ? visiblePasswordField
                : passwordField;

        field.requestFocus();
        field.positionCaret(field.getText().length());
    }

    @FXML
    private void handleLogin() {

        String username = usernameField.getText().trim();
        char[] password =
                passwordField.getText().toCharArray();

        if (username.isEmpty() || password.length == 0) {

            Arrays.fill(password, '\0');
            showError("Enter username and password.");
            return;
        }

        // Clear visible/hidden password fields
        passwordField.clear();

        loginButton.setDisable(true);
        messageLabel.setText("Checking credentials...");

        Task<Optional<User>> task = new Task<>() {

            @Override
            protected Optional<User> call() throws Exception {
                try {
                    return authService.authenticate(
                            username,
                            password
                    );
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };

        task.setOnSucceeded(event -> {

            loginButton.setDisable(false);

            Optional<User> result = task.getValue();

            if (result.isEmpty()) {
                showError("Invalid username or password.");
                return;
            }

            User user = result.get();

            SessionManager.startSession(user);

            try {
                NavigationUtil.navigate(
                        loginRoot,
                        NavigationUtil.dashboardResource(),
                        "FTMS - Dashboard"
                );

            } catch (IOException e) {
                SessionManager.logout();
                showError("Cannot open dashboard.");
                e.printStackTrace();
            }
        });

        task.setOnFailed(event -> {

            loginButton.setDisable(false);

            if (task.getException() instanceof SQLException) {
                showError("Cannot connect to the database.");
            } else {
                showError("Login failed.");
            }

            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task, "ftms-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: #ff9999;");
        messageLabel.setText(message);
    }
}
