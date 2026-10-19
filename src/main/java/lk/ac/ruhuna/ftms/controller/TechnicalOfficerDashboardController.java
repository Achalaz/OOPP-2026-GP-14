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

/** Dashboard presentation and navigation only; department operations are not connected. */
public class TechnicalOfficerDashboardController {
    @FXML private BorderPane technicalOfficerRoot;
    @FXML private ScrollPane contentScroll;
    @FXML private GridPane featureGrid;
    @FXML private Label nameLabel;
    @FXML private Label officerNameLabel;
    @FXML private Label avatarLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;

    private SessionIdentity identity;
    private int columns;

    @FXML
    public void initialize() {
        identity = SessionManager.requireSession();
        User officer = requireTechnicalOfficer();
        String name = officer.getFullName();
        name = name == null || name.isBlank() ? "Technical Officer" : name.strip();
        nameLabel.setText("Welcome back, " + name + "!");
        officerNameLabel.setText(name);
        avatarLabel.setText(name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
        dateLabel.setText(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)));
        contentScroll.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                arrangeCards(bounds.getWidth()));
        arrangeCards(contentScroll.getPrefViewportWidth());
    }

    private User requireTechnicalOfficer() {
        SessionManager.requireSession(identity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole())) {
            throw new SecurityException("An active Technical Officer session is required.");
        }
        return user;
    }

    private void arrangeCards(double viewportWidth) {
        double available = Math.max(0, viewportWidth - 56); // Main content's horizontal padding.
        int count = available >= 860 ? 3 : available >= 520 ? 2 : 1;
        if (columns == count) return;
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

    @FXML
    private void handleDashboard() {
        try {
            requireTechnicalOfficer();
            contentScroll.setVvalue(0);
            statusLabel.setText("Your faculty workspace. Select a service to get started.");
        } catch (SecurityException error) {
            showSessionError(error);
        }
    }

    @FXML
    private void handleProfile() {
        try {
            requireTechnicalOfficer();
            // Reuse the owner-bound profile workflow; username/password are not writable there.
            NavigationUtil.navigate(technicalOfficerRoot, "/fxml/profile.fxml", "FTMS - My Profile");
        } catch (SecurityException error) {
            showSessionError(error);
        } catch (IOException error) {
            statusLabel.setText("My Profile could not open. Please try again.");
            contentScroll.setVvalue(1);
        }
    }

    // AttendanceService verifies the account, department, and enrollment for each operation.
    @FXML private void handleAttendance() {
        try {
            requireTechnicalOfficer();
            NavigationUtil.navigate(technicalOfficerRoot, "/fxml/attendance.fxml", "FTMS - Attendance Management");
        } catch (SecurityException exception) {
            showSessionError(exception);
        } catch (IOException | RuntimeException exception) {
            statusLabel.setText("Unable to open Attendance Management. Please try again.");
            contentScroll.setVvalue(1);
        }
    }
    @FXML private void handleMedical() { showComingSoon("Medical Management"); }
    @FXML private void handleNotices() {
        try {
            requireTechnicalOfficer();
            NavigationUtil.navigate(technicalOfficerRoot, "/fxml/view_notices.fxml", "FTMS - Faculty Notices");
        } catch (SecurityException error) {
            showSessionError(error);
        } catch (IOException error) {
            statusLabel.setText("Unable to open Faculty Notices. Please try again.");
            contentScroll.setVvalue(1);
        }
    }
    // Timetable queries must be constrained by the officer's verified department.
    @FXML private void handleTimetable() { showComingSoon("Department Timetable"); }

    private void showComingSoon(String feature) {
        try {
            requireTechnicalOfficer();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("FTMS - " + feature);
            alert.setHeaderText(feature + " - Coming Soon");
            alert.setContentText("This feature will be available soon.");
            if (technicalOfficerRoot.getScene() != null && technicalOfficerRoot.getScene().getWindow() != null) {
                alert.initOwner(technicalOfficerRoot.getScene().getWindow());
            }
            alert.showAndWait();
        } catch (SecurityException error) {
            showSessionError(error);
        }
    }

    @FXML
    private void handleLogout() {
        if (SessionManager.isCurrentSession(identity)) {
            SessionManager.logout();
        } else if (SessionManager.isLoggedIn()) {
            statusLabel.setText("This dashboard belongs to an earlier session.");
            contentScroll.setVvalue(1);
            return;
        }
        technicalOfficerRoot.setDisable(true);
        try {
            NavigationUtil.navigate(technicalOfficerRoot, "/fxml/login.fxml", "FTMS - Login");
        } catch (IOException | RuntimeException error) {
            technicalOfficerRoot.setDisable(false);
            statusLabel.setText("You are logged out. The login screen could not open; please try Logout again.");
            contentScroll.setVvalue(1);
        }
    }

    private void showSessionError(SecurityException error) {
        statusLabel.setText(error.getMessage() + " Please use Logout to return to sign in.");
        contentScroll.setVvalue(1);
    }
}
