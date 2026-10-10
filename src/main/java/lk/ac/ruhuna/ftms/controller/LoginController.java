
package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

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
    private Button eyeButton;

    @FXML
    private Button loginButton;

    @FXML
    private Label messageLabel;

    private boolean passwordVisible = false;

    @FXML
    public void initialize() {

        // Resize background with window
        backgroundImage.fitWidthProperty()
                .bind(loginRoot.widthProperty());

        backgroundImage.fitHeightProperty()
                .bind(loginRoot.heightProperty());

        // Keep both password fields synchronized
        visiblePasswordField.textProperty()
                .bindBidirectional(
                        passwordField.textProperty()
                );

        // Password hidden by default
        passwordField.setVisible(true);
        visiblePasswordField.setVisible(false);

        passwordField.managedProperty()
                .bind(passwordField.visibleProperty());

        visiblePasswordField.managedProperty()
                .bind(visiblePasswordField.visibleProperty());

        eyeButton.setText("👁");
    }

    // Show or hide password
    @FXML
    private void togglePasswordVisibility() {

        passwordVisible = !passwordVisible;

        if (passwordVisible) {

            // Show password
            passwordField.setVisible(false);
            visiblePasswordField.setVisible(true);

            eyeButton.setText("🙈");

            visiblePasswordField.requestFocus();

            visiblePasswordField.positionCaret(
                    visiblePasswordField.getText().length()
            );

        } else {

            // Hide password
            visiblePasswordField.setVisible(false);
            passwordField.setVisible(true);

            eyeButton.setText("👁");

            passwordField.requestFocus();

            passwordField.positionCaret(
                    passwordField.getText().length()
            );
        }
    }

    // Temporary GUI-only login handler
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

        // Authentication will be added later
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
