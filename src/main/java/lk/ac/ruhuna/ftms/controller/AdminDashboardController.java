package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import lk.ac.ruhuna.ftms.controller.CourseController;
import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.service.CourseService;
import java.io.IOException;

public class AdminDashboardController {

    @FXML private javafx.scene.layout.BorderPane adminRoot;
    @FXML private javafx.scene.control.Label nameLabel;

    @FXML public void initialize() {
        var user = lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"ADMIN".equals(user.getRole()))
            throw new SecurityException("Active administrator access required.");
        nameLabel.setText("Welcome back, " + user.getFullName());
    }

    private void navigate(String fxmlFile) {
        try {
            lk.ac.ruhuna.ftms.util.NavigationUtil.navigate(adminRoot, "/fxml/" + fxmlFile, "FTMS");
        } catch (IOException | RuntimeException e) {
            showError("Unable to open the requested screen.");
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
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/courses.fxml"));

            Parent root = loader.load();

            CourseController controller = loader.getController();

            // Connect the CourseService here using your
            // existing admin session/role check.
            // Do not hardcode the admin check to true.

            // Display root using your existing dashboard navigation.

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleNotices() {
        navigate("notices.fxml");

    }

    @FXML
    private void handleTimetables() {
        showInfo("Timetable Management will be connected after CRUD implementation.");
    }

    @FXML
    private void handleProfile() {
        navigate("profile.fxml");
    }

    @FXML
    private void handleLogout() {
        lk.ac.ruhuna.ftms.util.SessionManager.logout();
        navigate("login.fxml");
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}

