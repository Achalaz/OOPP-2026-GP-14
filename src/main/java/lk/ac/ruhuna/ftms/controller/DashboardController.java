
package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

public class DashboardController {

    @FXML
    private Label nameLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label messageLabel;

    @FXML
    public void initialize() {

        User user = SessionManager.getCurrentUser();

        if (user == null) {
            throw new IllegalStateException(
                    "Login is required to open Dashboard"
            );
        }

        nameLabel.setText(
                "Welcome, " + user.getFullName()
        );

        roleLabel.setText(
                "Role: " + user.getRole()
        );
    }

    @FXML
    private void handleMyProfile() {
        try {
            NavigationUtil.navigate(nameLabel, "/fxml/profile.fxml", "FTMS - My Profile");
        } catch (Exception e) {
            messageLabel.setText("Cannot open My Profile. Please retry or sign in again.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {

        try {
            NavigationUtil.navigate(
                    nameLabel,
                    "/fxml/login.fxml",
                    "FTMS - Login"
            );

            SessionManager.logout();

        } catch (Exception e) {
            messageLabel.setText("Cannot open the login screen. Please retry logging out.");
            e.printStackTrace();
        }
    }
}
