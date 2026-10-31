package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO.EligibleStudent;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.Enrollment;
import lk.ac.ruhuna.ftms.service.AdminEnrollmentService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminEnrollmentController {
    @FXML private BorderPane adminEnrollmentsRoot;
    @FXML private VBox editorBox;
    @FXML private TextField studentSearch, offeringSearch, enrollmentSearch;
    @FXML private ComboBox<EligibleStudent> studentBox;
    @FXML private ComboBox<CourseOffering> offeringBox;
    @FXML private Spinner<Integer> attemptSpinner;
    @FXML private ComboBox<String> statusBox;
    @FXML private TableView<Enrollment> enrollmentTable;
    @FXML private Label offeringDetails, statusLabel;
    @FXML private Button refreshButton, updateButton;
    @FXML private ProgressIndicator progressIndicator;

    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();
    private final AdminEnrollmentService service = new AdminEnrollmentService();
    private Task<?> activeTask;
    private boolean disposed;
    private record Data(List<EligibleStudent> students, List<CourseOffering> offerings, List<Enrollment> enrollments) {}

    @FXML public void initialize() {
        service.requireSession();
        attemptSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        statusBox.getItems().setAll(AdminEnrollmentService.STATUSES);
        offeringBox.setConverter(new StringConverter<>() {
            public String toString(CourseOffering o) { return o == null ? "" : o.getCourseCode() + " - " + o.getCourseName()
                    + " | " + o.getAcademicYearName() + " | " + o.getSemesterName() + " | Group " + o.getOfferingGroup(); }
            public CourseOffering fromString(String ignored) { throw new UnsupportedOperationException(); }
        });
        offeringBox.valueProperty().addListener((value, old, o) -> offeringDetails.setText(o == null
                ? "Select an offering to see its details." : o.getCourseCode() + " - " + o.getCourseName()
                + "\n" + o.getSemesterName() + " | Academic year " + o.getAcademicYearName()
                + " | Group " + o.getOfferingGroup() + " | " + o.getStatus()));
        enrollmentTable.getSelectionModel().selectedItemProperty().addListener((value, old, row) -> {
            statusBox.setValue(row == null ? null : row.status());
            updateButton.setDisable(activeTask != null || row == null);
        });
        column("Enrollment", Enrollment::enrollmentId, 95);
        column("Student ID", Enrollment::studentId, 90);
        column("Registration", Enrollment::registrationNo, 125);
        column("Student", Enrollment::studentName, 170);
        column("Username", Enrollment::username, 110);
        column("Course Code", Enrollment::courseCode, 115);
        column("Course Name", Enrollment::courseName, 210);
        column("Semester", Enrollment::semester, 130);
        column("Academic Year", Enrollment::academicYear, 120);
        column("Group", Enrollment::offeringGroup, 75);
        column("Offering Status", Enrollment::offeringStatus, 125);
        column("Attempt", Enrollment::attempt, 80);
        column("Enrollment Status", Enrollment::status, 145);
        adminEnrollmentsRoot.sceneProperty().addListener((value, old, scene) -> {
            if (old != null && scene == null) {
                disposed = true;
                if (activeTask != null) activeTask.cancel(true);
                clearPrivateData();
            }
        });
        handleRefresh();
    }

    private void column(String title, Function<Enrollment, ?> value, double width) {
        var column = new TableColumn<Enrollment, String>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Objects.toString(value.apply(cell.getValue()), "")));
        column.setPrefWidth(width);
        enrollmentTable.getColumns().add(column);
    }

    @FXML private void handleRefresh() {
        String students = studentSearch.getText(), offerings = offeringSearch.getText(), query = enrollmentSearch.getText();
        run("Loading enrollments...", () -> new Data(service.findEligibleStudents(students),
                service.findAvailableOfferings(offerings), service.searchEnrollments(query)), data -> {
            studentBox.getItems().setAll(data.students());
            offeringBox.getItems().setAll(data.offerings());
            studentBox.setValue(null); offeringBox.setValue(null);
            populate(data.enrollments());
            statusLabel.setText(data.enrollments().size() + " enrollment(s). Select a student and offering to enroll.");
        });
    }

    @FXML private void handleStudentSearch() {
        String query = studentSearch.getText();
        run("Searching students...", () -> service.findEligibleStudents(query), students -> {
            studentBox.setValue(null); studentBox.getItems().setAll(students);
            statusLabel.setText(students.isEmpty() ? "No eligible students found. Check that the account is active and has a Student profile."
                    : students.size() + " student(s) found. Select a student.");
        });
    }
    @FXML private void handleOfferingSearch() {
        String query = offeringSearch.getText();
        run("Searching offerings...", () -> service.findAvailableOfferings(query), offerings -> {
            offeringBox.setValue(null); offeringBox.getItems().setAll(offerings);
            statusLabel.setText(offerings.isEmpty() ? "No planned or active offerings match your search."
                    : offerings.size() + " offering(s) found. Select an offering.");
        });
    }
    @FXML private void handleEnrollmentSearch() {
        String query = enrollmentSearch.getText();
        run("Searching enrollments...", () -> service.searchEnrollments(query), rows -> {
            populate(rows); statusLabel.setText(rows.size() + " enrollment(s).");
        });
    }
    @FXML private void handleEnroll() {
        var student = studentBox.getValue(); var offering = offeringBox.getValue();
        if (student == null || offering == null) { statusLabel.setText("Select a student and a course offering."); return; }
        final int attempt;
        try {
            attempt = Integer.parseInt(attemptSpinner.getEditor().getText().strip());
            if (attempt < 1) throw new NumberFormatException();
            attemptSpinner.getValueFactory().setValue(attempt);
        } catch (NumberFormatException error) { statusLabel.setText("Attempt number must be a positive whole number."); return; }
        run("Enrolling student...", () -> service.enrollStudent(student.studentId(), offering.getOfferingId(), attempt), id -> {
            enrollmentSearch.clear();
            reloadAfterWrite("Student enrolled successfully (enrollment " + id + ").");
        });
    }
    @FXML private void handleUpdateStatus() {
        var row = enrollmentTable.getSelectionModel().getSelectedItem(); String status = statusBox.getValue();
        if (row == null || status == null) { statusLabel.setText("Select an enrollment and status."); return; }
        run("Updating enrollment status...", () -> {
            service.updateEnrollmentStatus(row.enrollmentId(), row.status(), status); return true;
        }, ignored -> reloadAfterWrite("Enrollment status updated. Academic records were preserved."));
    }
    private void reloadAfterWrite(String message) {
        String query = enrollmentSearch.getText();
        run(message + " Refreshing...", () -> service.searchEnrollments(query), rows -> {
            populate(rows); statusLabel.setText(message);
        });
    }
    private void populate(List<Enrollment> rows) {
        enrollmentTable.getSelectionModel().clearSelection();
        enrollmentTable.getItems().setAll(rows);
        enrollmentTable.setPlaceholder(new Label("No enrollments found."));
    }

    private <T> void run(String message, Callable<T> work, Consumer<T> success) {
        if (disposed || activeTask != null) return;
        try { service.requireSession(); } catch (SecurityException error) { clearPrivateData(); statusLabel.setText(error.getMessage()); return; }
        Task<T> task = new Task<>() { protected T call() throws Exception { return work.call(); } };
        activeTask = task; busy(true); statusLabel.setText(message);
        task.setOnSucceeded(event -> { if (accept(task)) success.accept(task.getValue()); });
        task.setOnFailed(event -> {
            if (!accept(task)) return;
            Throwable error = task.getException();
            if (error instanceof SecurityException) clearPrivateData();
            statusLabel.setText(error instanceof SecurityException || error instanceof IllegalArgumentException
                    ? error.getMessage() : "Operation could not be confirmed. Refresh to check the latest records before retrying.");
        });
        task.setOnCancelled(event -> { if (accept(task)) statusLabel.setText("Operation cancelled. Refresh to check the latest records."); });
        Thread worker = new Thread(task, "admin-enrollments"); worker.setDaemon(true); worker.start();
    }
    private boolean accept(Task<?> task) {
        if (disposed || activeTask != task) return false;
        activeTask = null; busy(false);
        if (!SessionManager.isCurrentSession(identity)) {
            clearPrivateData(); statusLabel.setText("Your session has ended. Sign in again."); return false;
        }
        return true;
    }
    private void clearPrivateData() {
        enrollmentTable.getItems().clear(); studentBox.getItems().clear(); offeringBox.getItems().clear();
        studentBox.setValue(null); offeringBox.setValue(null);
    }
    private void busy(boolean busy) {
        editorBox.setDisable(busy); refreshButton.setDisable(busy); enrollmentTable.setDisable(busy);
        updateButton.setDisable(busy || enrollmentTable.getSelectionModel().getSelectedItem() == null);
        progressIndicator.setVisible(busy); progressIndicator.setManaged(busy);
    }
    @FXML private void handleDashboard() {
        try {
            service.requireSession();
            NavigationUtil.navigate(adminEnrollmentsRoot, "/fxml/admin_dashboard.fxml", "FTMS - Admin Dashboard");
        } catch (IOException | SecurityException error) { statusLabel.setText(error.getMessage()); }
    }
}
