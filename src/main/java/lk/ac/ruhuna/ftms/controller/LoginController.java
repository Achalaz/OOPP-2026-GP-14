package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
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
    private Label messageLabel;

    @FXML
    public void initialize() {

        // Resize background with window
        backgroundImage.fitWidthProperty()
                .bind(loginRoot.widthProperty());

        backgroundImage.fitHeightProperty()
                .bind(loginRoot.heightProperty());
    }

    @FXML
    private void handleLogin() {

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {

            messageLabel.setText(
                    "Please enter username and password."
            );

            return;
        }

        // Authentication will be connected in the next step.
        messageLabel.setText(
                "Database authentication is not connected yet."
        );
    }
}