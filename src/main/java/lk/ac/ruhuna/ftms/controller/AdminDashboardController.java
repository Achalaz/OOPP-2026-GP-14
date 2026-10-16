package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class AdminDashboardController {

    private void navigate(String fxmlFile) {
        try {
            URL location = getClass().getResource("/fxml/" + fxmlFile);

            if (location == null) {
                showError("FXML file not found: " + fxmlFile);
                return;
            }

            Parent root = FXMLLoader.load(location);

            Stage stage = (Stage) Stage.getWindows()
                    .stream()
                    .filter(window -> window.isShowing())
                    .findFirst()
                    .orElseThrow();

            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException | RuntimeException e) {
            showError("Navigation failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleDashboard() {
        navigate("admin_dashboard.fxml");
    }

    @FXML
    private void handleUsers() {
        navigate("Users.fxml");
    }

    @FXML
    private void handleCourses() {
        showInfo("Course Management will be connected after CRUD implementation.");
    }

    @FXML
    private void handleNotices() {
        showInfo("Notice Management will be connected after CRUD implementation.");
    }

    @FXML
    private void handleTimetables() {
        showInfo("Timetable Management will be connected after CRUD implementation.");
    }

    @FXML
    private void handleProfile() {
        showInfo("Connect this button to the existing Profile screen.");
    }

    @FXML
    private void handleLogout() {
        // TODO: Clear the authenticated session using
        // your project's SessionManager before navigating.
        showInfo("Logout integration is not complete yet.");
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}