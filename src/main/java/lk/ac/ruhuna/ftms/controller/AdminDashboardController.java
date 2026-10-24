
package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;

/**
 * Admin Dashboard Controller.
 *
 * Admin can manage:
 * - User Profiles
 * - Courses
 * - Notices
 * - Timetables
 */
public class AdminDashboardController {

    @FXML
    private BorderPane adminRoot;

    @FXML
    private Label nameLabel;

    // Initialize Admin Dashboard
    @FXML
    public void initialize() {

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole())) {

            throw new SecurityException(
                    "Active administrator access required."
            );
        }

        nameLabel.setText(
                "Welcome back, " + user.getFullName()
        );
    }

    // Common navigation method
    private void navigate(String fxmlFile) {

        try {

            NavigationUtil.navigate(
                    adminRoot,
                    "/fxml/" + fxmlFile,
                    "FTMS"
            );

        } catch (IOException | RuntimeException error) {

            error.printStackTrace();

            showError(
                    "Unable to open the requested screen."
            );
        }
    }

    // Admin Dashboard Home
    @FXML
    private void handleDashboard() {

        navigate("admin_dashboard.fxml");
    }

    // Manage Users
    @FXML
    private void handleUsers() {

        navigate("Users.fxml");
    }

    // Manage Courses
    @FXML
    private void handleCourses() {

        navigate("courses.fxml");
    }

    // Manage Notices
    @FXML
    private void handleNotices() {

        navigate("notices.fxml");
    }

    // NEW: Admin Timetable Management
    @FXML
    private void handleTimetables() {

        navigate("admin_timetable.fxml");
    }

    // Admin Profile
    @FXML
    private void handleProfile() {

        navigate("profile.fxml");
    }

    // Admin Logout
    @FXML
    private void handleLogout() {

        SessionManager.logout();

        navigate("login.fxml");
    }

    // Display error message
    private void showError(String message) {

        Alert alert = new Alert(
                Alert.AlertType.ERROR,
                message
        );

        alert.setTitle("FTMS Admin Dashboard");
        alert.setHeaderText(null);
        alert.showAndWait();
    }


    @FXML
    private void handleCourseOfferings() {
        navigate("course_offerings.fxml");
    }

    @FXML
    private void handleEnrollments() {
        navigate("admin_enrollments.fxml");
    }

}
