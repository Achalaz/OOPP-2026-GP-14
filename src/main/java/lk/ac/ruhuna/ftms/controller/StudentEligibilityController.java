
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.dao.StudentEligibilityDAO.StudentEligibility;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.StudentEligibilityService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Read-only eligibility screen for the signed-in undergraduate.
 */
public final class StudentEligibilityController {

    @FXML private BorderPane studentEligibilityRoot;

    @FXML private Label studentNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;

    @FXML private Button refreshButton;
    @FXML private Button backButton;

    // Student eligibility table

    @FXML private TableView<StudentEligibility> eligibilityTable;

    @FXML private TableColumn<StudentEligibility, String>
            enrollmentColumn;

    @FXML private TableColumn<StudentEligibility, String>
            courseCodeColumn;

    @FXML private TableColumn<StudentEligibility, String>
            courseNameColumn;

    @FXML private TableColumn<StudentEligibility, String>
            caPercentageColumn;

    @FXML private TableColumn<StudentEligibility, String>
            attendancePercentageColumn;

    @FXML private TableColumn<StudentEligibility, String>
            decisionColumn;

    @FXML private TableColumn<StudentEligibility, String>
            reasonColumn;

    private final ObservableList<StudentEligibility> rows =
            FXCollections.observableArrayList();

    private final SimpleBooleanProperty busy =
            new SimpleBooleanProperty(false);

    private SessionIdentity loginIdentity;
    private StudentEligibilityService eligibilityService;

    // Initialize eligibility page

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        User student = requireStudent();

        eligibilityService = new StudentEligibilityService();

        String name = student.getFullName();

        studentNameLabel.setText(
                "My Eligibility - "
                        + (name == null || name.isBlank()
                        ? "Student"
                        : name)
        );

        // Enrollment ID

        enrollmentColumn.setCellValueFactory(
                cell -> text(
                        Integer.toString(
                                cell.getValue().enrollmentId()
                        )
                )
        );

        // Course Code

        courseCodeColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().courseCode()
                )
        );

        // Course Name

        courseNameColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().courseName()
                )
        );

        // CA Percentage

        caPercentageColumn.setCellValueFactory(
                cell -> text(
                        percentage(
                                cell.getValue().caPercentage()
                        )
                )
        );

        // Attendance Percentage

        attendancePercentageColumn.setCellValueFactory(
                cell -> text(
                        percentage(
                                cell.getValue().attendancePercentage()
                        )
                )
        );

        // Eligibility Decision

        decisionColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().getDecision()
                )
        );

        // Eligibility Reason

        reasonColumn.setCellValueFactory(
                cell -> text(
                        displayText(
                                cell.getValue().reason()
                        )
                )
        );

        eligibilityTable.setItems(rows);

        eligibilityTable.setPlaceholder(
                new Label(
                        "No enrolled courses or eligibility records found."
                )
        );

        // Disable controls during loading

        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);
        eligibilityTable.disableProperty().bind(busy);

        summaryLabel.setText(
                "Eligibility results have not been loaded yet."
        );

        loadEligibility();
    }

    // Refresh eligibility results

    @FXML
    private void handleRefresh() {

        try {

            requireStudent();
            loadEligibility();

        } catch (SecurityException error) {

            rows.clear();

            showError(
                    "Your student session has ended. Please sign in again.",
                    error
            );
        }
    }

    // Back to ORIGINAL Student Dashboard

    @FXML
    private void handleBack() {

        try {

            requireStudent();

            NavigationUtil.navigate(
                    studentEligibilityRoot,
                    "/fxml/student_dashboard.fxml",
                    "FTMS - Student Dashboard"
            );

        } catch (SecurityException error) {

            showError(
                    "Your student session has ended. Please sign in again.",
                    error
            );

        } catch (IOException | RuntimeException error) {

            showError(
                    "Unable to return to the Student Dashboard.",
                    error
            );
        }
    }

    // Verify logged-in student

    private User requireStudent() {

        SessionManager.requireSession(loginIdentity);

        User student = SessionManager.getCurrentUser();

        if (student == null
                || !student.isActive()
                || !"STUDENT".equals(student.getRole())
                || student.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        return student;
    }

    // Load student's eligibility records

    private void loadEligibility() {

        if (busy.get()) {
            return;
        }

        busy.set(true);

        statusLabel.setText(
                "Loading your eligibility records..."
        );

        Task<List<StudentEligibility>> task = new Task<>() {

            @Override
            protected List<StudentEligibility> call()
                    throws Exception {

                return eligibilityService.getMyEligibility();
            }
        };

        // Database query succeeded

        task.setOnSucceeded(event -> {

            busy.set(false);

            try {

                requireStudent();

                List<StudentEligibility> result =
                        task.getValue();

                rows.setAll(result);

                updateSummary(result);

                statusLabel.setText(
                        result.isEmpty()
                                ? "No course enrollments found for this student."
                                : result.size()
                                + " course eligibility records loaded."
                );

            } catch (SecurityException error) {

                rows.clear();

                summaryLabel.setText(
                        "Session expired."
                );

                showError(
                        "Your student session has ended. Please sign in again.",
                        error
                );

            } catch (RuntimeException error) {

                showError(
                        "Unable to display eligibility records.",
                        error
                );
            }
        });

        // Database query failed

        task.setOnFailed(event -> {

            busy.set(false);

            rows.clear();

            summaryLabel.setText(
                    "Eligibility records could not be loaded."
            );

            Throwable error = task.getException();

            if (error instanceof SecurityException) {

                showError(
                        "Your student session has ended. Please sign in again.",
                        error
                );

            } else {

                showError(
                        "Unable to load eligibility records. "
                                + "Check the database connection "
                                + "and table columns.",
                        error
                );
            }
        });

        Thread worker = new Thread(
                task,
                "ftms-student-eligibility"
        );

        worker.setDaemon(true);
        worker.start();
    }

    // Calculate displayed summary counts

    private void updateSummary(
            List<StudentEligibility> result) {

        long eligible = result.stream()
                .filter(row ->
                        "ELIGIBLE".equals(row.getDecision())
                )
                .count();

        long notEligible = result.stream()
                .filter(row ->
                        "NOT ELIGIBLE".equals(row.getDecision())
                )
                .count();

        long pending =
                result.size() - eligible - notEligible;

        summaryLabel.setText(
                "Courses: " + result.size()
                        + "  |  Eligible: " + eligible
                        + "  |  Not Eligible: " + notEligible
                        + "  |  Pending: " + pending
        );
    }

    // Table text formatting

    private static ReadOnlyStringWrapper text(
            String value) {

        return new ReadOnlyStringWrapper(
                value == null ? "" : value
        );
    }

    // Format percentage

    private static String percentage(Double value) {

        return value == null
                ? "-"
                : String.format(
                Locale.ROOT,
                "%.2f%%",
                value
        );
    }

    // Format missing reasons

    private static String displayText(String value) {

        return value == null || value.isBlank()
                ? "-"
                : value;
    }

    // Display errors

    private void showError(
            String message,
            Throwable error) {

        statusLabel.setText(message);

        if (error != null) {
            error.printStackTrace();
        }

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        alert.setTitle("Student Eligibility");
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (studentEligibilityRoot.getScene() != null
                && studentEligibilityRoot.getScene()
                .getWindow() != null) {

            alert.initOwner(
                    studentEligibilityRoot
                            .getScene()
                            .getWindow()
            );
        }

        alert.showAndWait();
    }
}
