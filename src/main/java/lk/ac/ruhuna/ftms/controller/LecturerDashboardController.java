
package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LecturerDashboardController {

    @FXML
    private BorderPane lecturerRoot;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private Label dateLabel;

    private SessionIdentity identity;

    @FXML
    public void initialize() {

        User lecturer = SessionManager.getCurrentUser();

        if (lecturer == null ||
                !lecturer.isActive() ||
                !"LECTURER".equals(lecturer.getRole())) {

            throw new SecurityException(
                    "Only lecturers can access this dashboard."
            );
        }

        identity = SessionManager.requireSession();

        welcomeLabel.setText(
                "Welcome back, " + lecturer.getFullName() + "!"
        );

        usernameLabel.setText(
                lecturer.getFullName() + "  \u2022  LECTURER"
        );

        dateLabel.setText(
                LocalDate.now().format(
                        DateTimeFormatter.ofPattern(
                                "EEEE, dd MMMM yyyy"
                        )
                )
        );
    }

    private void verifyLecturer() {

        SessionManager.requireSession(identity);

        User user = SessionManager.getCurrentUser();

        if (user == null ||
                !user.isActive() ||
                !"LECTURER".equals(user.getRole())) {

            throw new SecurityException(
                    "Lecturer access is required."
            );
        }
    }

    private void openScreen(String fxml, String title) {

        try {
            verifyLecturer();

            NavigationUtil.navigate(
                    lecturerRoot,
                    fxml,
                    title
            );

        } catch (IOException | SecurityException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleDashboard() {
        openScreen(
                "/fxml/lecturer_dashboard.fxml",
                "FTMS - Lecturer Dashboard"
        );
    }

    @FXML
    private void handleProfile() {
        openScreen(
                "/fxml/profile.fxml",
                "FTMS - My Profile"
        );
    }

    @FXML
    private void handleCourseMaterials() {
        openWorkspace("materials", "Course Materials");
    }

    @FXML
    private void handleAssessments() {
        openScreen("/fxml/assessments.fxml", "FTMS - Assessment Management");
    }

    @FXML
    private void handleResults() {
        openScreen("/fxml/results.fxml", "FTMS - Result Publication");
    }

    @FXML
    private void handleUploadMarks() {
        openScreen(
                "/fxml/marks_entry.fxml",
                "FTMS - Marks Entry"
        );
    }

    @FXML
    private void handleStudentDetails() {
        openWorkspace("students", "Undergraduate Details");
    }

    @FXML
    private void handleEligibility() {
        openScreen("/fxml/eligibility.fxml", "FTMS - Eligibility");
    }

    @FXML
    private void handleAcademicReports() {
        openScreen("/fxml/reports.fxml", "FTMS - Academic Reports");
    }

    @FXML
    private void handleGPA() {
        openScreen("/fxml/gpa.fxml", "FTMS - SGPA / CGPA");
    }

    @FXML
    private void handleAttendance() {
        openWorkspace("attendance", "Attendance Summaries");
    }

    @FXML
    private void handleMedical() {
        openWorkspace("medical", "Medical Statuses");
    }

    @FXML
    private void handleNotices() {
        openScreen("/fxml/view_notices.fxml", "FTMS - Faculty Notices");
    }

    @FXML
    private void handleLogout() {

        try {
            SessionManager.logout();

            NavigationUtil.navigate(
                    lecturerRoot,
                    "/fxml/login.fxml",
                    "FTMS - Login"
            );

            SessionManager.logout();

        } catch (IOException | SecurityException e) {
            showError("Logout failed: " + e.getMessage());
        }
    }

    private void openWorkspace(String mode, String title) {
        try {
            verifyLecturer();
            var loader=new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/lecturer_workspace.fxml"));
            javafx.scene.Parent root=loader.load();
            LecturerWorkspaceController controller=loader.getController();
            lecturerRoot.getScene().setRoot(root);
            controller.configure(mode,title);
        } catch(IOException | SecurityException error) {showError("Cannot open lecturer workspace.");}
    }

    private void showPending(String title, String message) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title + " - Coming Soon");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("FTMS Error");
        alert.setHeaderText("Operation failed");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
