package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Student portal navigation; academic screens enforce student self-only access. */
public class StudentDashboardController {
    @FXML private BorderPane studentRoot;
    @FXML private ScrollPane contentScroll;
    @FXML private GridPane featureGrid;
    @FXML private Label nameLabel;
    @FXML private Label studentNameLabel;
    @FXML private Label avatarLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;

    private SessionIdentity identity;
    private int columns;

    @FXML
    public void initialize() {
        identity = SessionManager.requireSession();
        User student = requireStudent();
        String name = student.getFullName();
        if (name == null || name.isBlank()) {
            name = "Student";
        }
        name = name.strip();
        nameLabel.setText("Welcome back, " + name + "!");
        studentNameLabel.setText(name);
        avatarLabel.setText(name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)));

        contentScroll.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                arrangeCards(bounds.getWidth()));
        arrangeCards(contentScroll.getPrefViewportWidth());

    }

    private User requireStudent() {
        SessionManager.requireSession(identity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"STUDENT".equals(user.getRole())) {
            throw new SecurityException("An active student session is required.");
        }
        return user;
    }

    private void arrangeCards(double viewportWidth) {
        // The main content has 28px padding on either side; cards remain at least 230px wide.
        double available = Math.max(0, viewportWidth - 56);
        int count = available >= 738 ? 3 : available >= 476 ? 2 : 1;
        if (columns == count) {
            return;
        }
        columns = count;
        featureGrid.getColumnConstraints().clear();
        for (int column = 0; column < count; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / count);
            constraint.setMinWidth(0);
            constraint.setHgrow(Priority.ALWAYS);
            featureGrid.getColumnConstraints().add(constraint);
        }
        for (int index = 0; index < featureGrid.getChildren().size(); index++) {
            var card = featureGrid.getChildren().get(index);
            GridPane.setColumnIndex(card, index % count);
            GridPane.setRowIndex(card, index / count);
        }
    }

    @FXML private void handleDashboard() {
        try {
            requireStudent();
            contentScroll.setVvalue(0);
            statusLabel.setText("Your student workspace. Select a feature to explore.");
        } catch (SecurityException error) {
            showSessionError(error);
        }
    }

    @FXML private void handleProfile() { openAcademicScreen("/fxml/profile.fxml", "My Profile"); }
    @FXML private void handleAttendance() { openAcademicScreen("/fxml/student_attendance.fxml", "My Attendance"); }
    @FXML private void handleMedical() { openAcademicScreen("/fxml/student_medical.fxml", "My Medical"); }
    @FXML private void handleCourses() { openAcademicScreen("/fxml/student_courses.fxml", "My Courses"); }
    @FXML private void handleGrades() { openAcademicScreen("/fxml/student_results.fxml", "My Grades and GPA"); }
    @FXML private void handleGPA() { openAcademicScreen("/fxml/student_results.fxml", "My Grades and GPA"); }
    @FXML private void handleTimetable() { showComingSoon("My Timetable"); }

    private void openAcademicScreen(String resource, String title) {
        try {
            requireStudent();
            NavigationUtil.navigate(studentRoot, resource, "FTMS - " + title);
        } catch (SecurityException error) {
            showSessionError(error);
        } catch (IOException error) {
            statusLabel.setText("Unable to open " + title + ". Please try again.");
            contentScroll.setVvalue(1);
        }
    }
    @FXML private void handleNotices() {
        try {
            requireStudent();
            NavigationUtil.navigate(studentRoot, "/fxml/view_notices.fxml", "FTMS - Faculty Notices");
        } catch (SecurityException error) {
            showSessionError(error);
        } catch (IOException error) {
            statusLabel.setText("Unable to open Faculty Notices. Please try again.");
            contentScroll.setVvalue(1);
        }
    }

    private void showComingSoon(String feature) {
        try {
            requireStudent();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("FTMS - " + feature);
            alert.setHeaderText(feature);
            alert.setContentText("This feature will be available soon.");
            if (studentRoot.getScene() != null && studentRoot.getScene().getWindow() != null) {
                alert.initOwner(studentRoot.getScene().getWindow());
            }
            alert.showAndWait();
        } catch (SecurityException error) {
            showSessionError(error);
        }
    }

    @FXML
    private void handleLogout() {
        // An old controller must not end a newer login's session.
        if (SessionManager.isCurrentSession(identity)) {
            SessionManager.logout();
        } else if (SessionManager.isLoggedIn()) {
            statusLabel.setText("This dashboard belongs to an earlier session.");
            return;
        }
        studentRoot.setDisable(true);
        try {
            NavigationUtil.navigate(studentRoot, "/fxml/login.fxml", "FTMS - Login");
        } catch (IOException | RuntimeException error) {
            studentRoot.setDisable(false);
            statusLabel.setText("You are logged out. The login screen could not open; please try Logout again.");
        }
    }

    private void showSessionError(SecurityException error) {
        statusLabel.setText(error.getMessage() + " Please use Logout to return to sign in.");
    }
}
