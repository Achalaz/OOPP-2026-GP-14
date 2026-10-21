package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;

import lk.ac.ruhuna.ftms.controller.CourseOfferingController;
import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;

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
        navigate("courses.fxml");
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


    @FXML
    private void handleCourseOfferings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/course_offerings.fxml"
                    )
            );

            Parent root = loader.load();

            CourseOfferingController controller =
                    loader.getController();

            // Inject the configured service here.
            // Use your existing admin-session check.

            // Display the screen using your dashboard's
            // existing navigation container.

        } catch (IOException e) {
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Navigation Error");
            alert.setHeaderText("Unable to open Course Offerings");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

}

