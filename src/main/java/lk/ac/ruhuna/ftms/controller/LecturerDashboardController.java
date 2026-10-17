
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
                lecturer.getFullName() + "  •  LECTURER"
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
        showPending(
                "Course Materials",
                "We will add upload, edit, download and " +
                        "remove features for courses assigned " +
                        "to the lecturer."
        );
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
        showPending(
                "Undergraduate Details",
                "A read-only student list and " +
                        "student details screen will be added."
        );
    }

    @FXML
    private void handleEligibility() {
        showPending(
                "Student Eligibility",
                "A lecturer read-only eligibility " +
                        "screen will be connected here."
        );
    }

    @FXML
    private void handleAcademicReports() {
        openScreen(
                "/fxml/reports.fxml",
                "FTMS - Student Result Reports"
        );
    }

    @FXML
    private void handleGPA() {
        showPending(
                "Student GPA",
                "A read-only SGPA / CGPA view " +
                        "will be connected here."
        );
    }

    @FXML
    private void handleAttendance() {
        showPending(
                "Attendance Records",
                "A read-only attendance records " +
                        "screen will be added for lecturers."
        );
    }

    @FXML
    private void handleMedical() {
        showPending(
                "Medical Records",
                "A read-only medical records " +
                        "screen will be added for lecturers."
        );
    }

    @FXML
    private void handleNotices() {
        showPending(
                "Notices",
                "Lecturers will be able to view " +
                        "published notices here."
        );
    }

    @FXML
    private void handleLogout() {

        try {
            verifyLecturer();

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
