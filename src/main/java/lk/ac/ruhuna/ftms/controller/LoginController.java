package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.Objects;

public class LoginController {

    @FXML
    private StackPane loginRoot;

    @FXML
    private ImageView backgroundImage;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField visiblePasswordField;

    @FXML
    private ImageView eyeIcon;

    @FXML
    private Label messageLabel;

    private boolean passwordVisible = false;

    private Image visibleIcon;
    private Image hiddenIcon;

    @FXML
    public void initialize() {

        // Resize background
        backgroundImage.fitWidthProperty()
                .bind(loginRoot.widthProperty());

        backgroundImage.fitHeightProperty()
                .bind(loginRoot.heightProperty());

        // Load custom eye images
        visibleIcon = loadImage("/images/visible.png");
        hiddenIcon = loadImage("/images/hidden.png");

        // Synchronize both password fields
        visiblePasswordField.textProperty()
                .bindBidirectional(
                        passwordField.textProperty()
                );

        // Hide password initially
        passwordField.setVisible(true);
        visiblePasswordField.setVisible(false);

        passwordField.managedProperty()
                .bind(passwordField.visibleProperty());

        visiblePasswordField.managedProperty()
                .bind(visiblePasswordField.visibleProperty());

        // Show eye-open icon initially
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

    // Show / Hide password
    @FXML
    private void togglePasswordVisibility() {

        passwordVisible = !passwordVisible;

        passwordField.setVisible(!passwordVisible);
        visiblePasswordField.setVisible(passwordVisible);

        if (passwordVisible) {

            // Show password
            eyeIcon.setImage(hiddenIcon);

            visiblePasswordField.requestFocus();

            visiblePasswordField.positionCaret(
                    visiblePasswordField.getText().length()
            );

        } else {

            // Hide password
            eyeIcon.setImage(visibleIcon);

            passwordField.requestFocus();

            passwordField.positionCaret(
                    passwordField.getText().length()
            );
        }
    }

    // Temporary GUI-only login test
    @FXML
    private void handleLogin() {

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty()) {

            showError("Please enter your username.");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {

            showError("Please enter your password.");

            if (passwordVisible) {
                visiblePasswordField.requestFocus();
            } else {
                passwordField.requestFocus();
            }

            return;
        }

        messageLabel.setStyle(
                "-fx-text-fill: #90EE90;"
        );

        messageLabel.setText(
                "GUI test successful. Authentication not connected."
        );
    }

    private void showError(String message) {

        messageLabel.setStyle(
                "-fx-text-fill: #ff9999;"
        );

        messageLabel.setText(message);
    }
}
