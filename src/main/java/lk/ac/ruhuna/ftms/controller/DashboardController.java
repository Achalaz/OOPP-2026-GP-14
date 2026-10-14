
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
    private void handleLogout() {

        try {
            NavigationUtil.navigate(
                    nameLabel,
                    "/fxml/login.fxml",
                    "FTMS - Login"
            );

            SessionManager.logout();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
