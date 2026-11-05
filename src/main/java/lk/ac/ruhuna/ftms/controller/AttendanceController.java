
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.AttendanceEnrollment;
import lk.ac.ruhuna.ftms.model.AttendanceRecord;
import lk.ac.ruhuna.ftms.model.AttendanceSession;
import lk.ac.ruhuna.ftms.model.AttendanceSummary;
import lk.ac.ruhuna.ftms.service.AttendanceService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class AttendanceController {

    private static final int MAX_SESSIONS_PER_TYPE = 15;

    // Attendance Session Controls
    @FXML private ComboBox<AttendanceOffering> offeringCombo;
    @FXML private DatePicker sessionDatePicker;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private ComboBox<String> sessionTypeCombo;
    @FXML private TextField topicField;
    @FXML private BorderPane attendanceRoot;
    @FXML private VBox workspace;

    // Attendance Record Controls
    @FXML private ComboBox<AttendanceSession> sessionCombo;
    @FXML private ComboBox<AttendanceEnrollment> enrollmentCombo;
    @FXML private ComboBox<String> attendanceStatusCombo;

    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, Integer> enrollmentColumn;
    @FXML private TableColumn<AttendanceRecord, String> statusColumn;

    @FXML private Label statusLabel;
    @FXML private Button saveSessionButton;
    @FXML private Button saveAttendanceButton;
    @FXML private Button loadAttendanceButton;
    @FXML private Button loadSummaryButton;

    // STEP 19 - Session Progress
    @FXML private Label sessionProgressLabel;

    // Attendance Summary Controls
    @FXML private TableView<AttendanceSummary> summaryTable;

    @FXML private TableColumn<AttendanceSummary, Integer>
            summaryEnrollmentColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryRegistrationColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryStudentColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryTheoryColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryPracticalColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryCombinedColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryMedicalColumn;

    @FXML private TableColumn<AttendanceSummary, String>
            summaryUnmarkedColumn;

    @FXML private Label summaryDetailsLabel;

    private final AttendanceService attendanceService;

    private boolean busy;
    private boolean sceneWasAttached;
    private Task<?> activeTask;

    private final ObservableList<AttendanceRecord> attendanceRecords =
            FXCollections.observableArrayList();

    private final ObservableList<AttendanceSummary> summaryRows =
            FXCollections.observableArrayList();

    public AttendanceController() {
        this(new AttendanceService());
    }

    AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    // =============================================
    // INITIALIZATION
    // =============================================

    @FXML
    public void initialize() {

        attendanceRoot.sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        sceneWasAttached = true;
                    }
                }
        );

        attendanceService.requireManagementSession();

        sessionTypeCombo.setItems(
                FXCollections.observableArrayList(
                        "THEORY",
                        "PRACTICAL",
                        "TUTORIAL",
                        "OTHER"
                )
        );

        attendanceStatusCombo.setItems(
                FXCollections.observableArrayList(
                        "PRESENT",
                        "ABSENT",
                        "LATE",
                        "EXCUSED",
                        "MEDICAL"
                )
        );

        enrollmentColumn.setCellValueFactory(
                data -> new SimpleIntegerProperty(
                        data.getValue().getEnrollmentId()
                ).asObject()
        );

        statusColumn.setCellValueFactory(
                data -> new SimpleStringProperty(
                        data.getValue().getAttendanceStatus()
                )
        );

        attendanceTable.setItems(attendanceRecords);

        initializeSummaryTable();

        sessionDatePicker.setValue(LocalDate.now());

        offeringCombo.setDisable(true);

        sessionCombo.setConverter(new StringConverter<>() {

            @Override
            public String toString(AttendanceSession session) {

                if (session == null) {
                    return "";
                }

                return "#" + session.getSessionId()
                        + " | " + session.getSessionDate()
                        + " | " + (
                        session.getStartTime() == null
                                ? "Time not recorded"
                                : session.getStartTime()
                )
                        + " | " + session.getSessionType();
            }

            @Override
            public AttendanceSession fromString(String value) {
                return null;
            }
        });

        attendanceTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        attendanceTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, row) -> {

                    if (row != null) {
                        selectEnrollment(row.getEnrollmentId());

                        attendanceStatusCombo.setValue(
                                row.getAttendanceStatus()
                        );
                    }
                });

        // Change selected course offering
        offeringCombo.valueProperty().addListener(
                (obs, old, choice) -> {

                    clearOfferingData();

                    if (choice != null) {
                        loadOffering(choice.offeringId());
                    }

                    updateControlState();
                }
        );

        sessionCombo.valueProperty().addListener(
                (obs, old, choice) -> {

                    attendanceRecords.clear();
                    attendanceStatusCombo.setValue(null);

                    updateControlState();
                }
        );

        enrollmentCombo.valueProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        attendanceStatusCombo.valueProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        sessionDatePicker.valueProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        startTimeField.textProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        endTimeField.textProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        topicField.textProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        sessionTypeCombo.valueProperty().addListener(
                (obs, old, choice) -> updateControlState()
        );

        updateSessionProgress();
        updateControlState();

        run(
                "Loading authorized course offerings...",
                attendanceService::getAuthorizedOfferings,
                choices -> {

                    offeringCombo.getItems().setAll(choices);

                    statusLabel.setText(
                            choices.isEmpty()
                                    ? "No course offerings are assigned to your department."
                                    : "Select a course offering from your department to begin."
                    );

                    updateControlState();
                }
        );
    }

    // =============================================
    // ATTENDANCE SUMMARY TABLE
    // =============================================

    private void initializeSummaryTable() {

        summaryEnrollmentColumn.setCellValueFactory(
                cell -> new SimpleIntegerProperty(
                        cell.getValue().getEnrollmentId()
                ).asObject()
        );

        summaryRegistrationColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getRegistrationNo()
                )
        );

        summaryStudentColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getStudentName()
                )
        );

        summaryTheoryColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getTheoryPresent(),
                                cell.getValue().getTheorySessions(),
                                cell.getValue().getTheoryPercentage()
                        )
                )
        );

        summaryPracticalColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getPracticalPresent(),
                                cell.getValue().getPracticalSessions(),
                                cell.getValue().getPracticalPercentage()
                        )
                )
        );

        summaryCombinedColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getTotalPresent(),
                                cell.getValue().getTotalSessions(),
                                cell.getValue().getCombinedPercentage()
                        )
                )
        );

        summaryMedicalColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getMedicalCount()
                        )
                )
        );

        summaryUnmarkedColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getUnmarkedCount()
                        )
                )
        );

        summaryTable.setItems(summaryRows);

        summaryTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {

                    if (selected != null) {

                        selectEnrollment(
                                selected.getEnrollmentId()
                        );

                        summaryDetailsLabel.setText(
                                selected.getRegistrationNo()
                                        + " - "
                                        + selected.getStudentName()

                                        + " | Theory: "
                                        + formatPercentage(
                                        selected.getTheoryPercentage()
                                )

                                        + " | Practical: "
                                        + formatPercentage(
                                        selected.getPracticalPercentage()
                                )

                                        + " | Combined: "
                                        + formatPercentage(
                                        selected.getCombinedPercentage()
                                )

                                        + " | Late: "
                                        + selected.getLateCount()

                                        + " | Excused: "
                                        + selected.getExcusedCount()

                                        + " | Medical: "
                                        + selected.getMedicalCount()

                                        + " | Unmarked: "
                                        + selected.getUnmarkedCount()
                        );

                    } else {

                        summaryDetailsLabel.setText(
                                "Select a student to view individual attendance details."
                        );
                    }
                });
    }

    // =============================================
    // SAVE ATTENDANCE SESSION
    // =============================================

    @FXML
    private void handleSaveSession() {

        if (busy) {
            return;
        }

        try {

            attendanceService.requireManagementSession();

            var offering = required(
                    offeringCombo.getValue(),
                    "Select a course offering."
            );

            var session = new AttendanceSession(
                    offering.offeringId(),
                    sessionDatePicker.getValue(),
                    LocalTime.parse(startTimeField.getText().trim()),
                    LocalTime.parse(endTimeField.getText().trim()),
                    sessionTypeCombo.getValue(),
                    topicField.getText().trim(),
                    null
            );

            run(
                    "Saving attendance session...",

                    () -> {

                        attendanceService.saveSession(session);

                        try {

                            return attendanceService.getSessionsByOffering(
                                    offering.offeringId()
                            );

                        } catch (SecurityException exception) {

                            throw exception;

                        } catch (RuntimeException exception) {

                            throw new IllegalStateException(
                                    "Session #" + session.getSessionId()
                                            + " was saved, but the list could not refresh. "
                                            + "Reopen Attendance before creating another session.",
                                    exception
                            );
                        }
                    },

                    sessions -> {

                        sessionCombo.getItems().setAll(sessions);

                        // STEP 19: Refresh progress
                        updateSessionProgress();

                        sessions.stream()
                                .filter(row ->
                                        row.getSessionId() == session.getSessionId()
                                )
                                .findFirst()
                                .ifPresent(sessionCombo::setValue);

                        summaryRows.clear();

                        statusLabel.setText(
                                "Attendance session saved. "
                                        + "Select a student and attendance status."
                        );
                    }
            );

        } catch (Exception exception) {

            showError(
                    exception instanceof java.time.format.DateTimeParseException
                            ? "Enter start and end times in HH:mm format, for example 09:00."
                            : exception.getMessage()
            );
        }
    }

    // =============================================
    // SAVE STUDENT ATTENDANCE
    // =============================================

    @FXML
    private void handleSaveAttendance() {

        if (busy) {
            return;
        }

        try {

            attendanceService.requireManagementSession();

            var session = required(
                    sessionCombo.getValue(),
                    "Select an attendance session."
            );

            var enrollment = required(
                    enrollmentCombo.getValue(),
                    "Select an undergraduate."
            );

            var status = required(
                    attendanceStatusCombo.getValue(),
                    "Select an attendance status."
            );

            var record = new AttendanceRecord(
                    session.getSessionId(),
                    enrollment.enrollmentId(),
                    status,
                    null
            );

            run(
                    "Saving student attendance...",

                    () -> {

                        attendanceService.saveAttendance(record);

                        return attendanceService.getAttendanceBySession(
                                session.getSessionId()
                        );
                    },

                    records -> {

                        attendanceRecords.setAll(records);

                        summaryRows.clear();

                        statusLabel.setText(
                                "Attendance saved and reloaded. "
                                        + "Load the summary to refresh percentages."
                        );
                    }
            );

        } catch (Exception exception) {

            showError(exception.getMessage());
        }
    }

    // =============================================
    // LOAD ATTENDANCE
    // =============================================

    @FXML
    private void handleLoadAttendance() {

        if (busy) {
            return;
        }

        try {

            var session = required(
                    sessionCombo.getValue(),
                    "Select an attendance session."
            );

            attendanceRecords.clear();

            run(
                    "Loading saved attendance...",

                    () -> attendanceService.getAttendanceBySession(
                            session.getSessionId()
                    ),

                    rows -> {

                        attendanceRecords.setAll(rows);

                        statusLabel.setText(
                                rows.isEmpty()
                                        ? "No attendance has been marked for this session."
                                        : "Saved attendance loaded. Select a row to update its status."
                        );
                    }
            );

        } catch (Exception exception) {

            showError(exception.getMessage());
        }
    }

    // =============================================
    // LOAD ATTENDANCE SUMMARY
    // =============================================

    @FXML
    private void handleLoadSummary() {

        if (busy) {
            return;
        }

        try {

            var offering = required(
                    offeringCombo.getValue(),
                    "Select a course offering."
            );

            summaryRows.clear();

            run(
                    "Loading attendance summary...",

                    () -> attendanceService.getSummariesByOffering(
                            offering.offeringId()
                    ),

                    rows -> {

                        summaryRows.setAll(rows);

                        statusLabel.setText(
                                rows.isEmpty()
                                        ? "No undergraduate enrollments found for this offering."
                                        : "Loaded " + rows.size()
                                        + " student summaries. Select a row for details."
                        );
                    }
            );

        } catch (Exception exception) {

            showError(exception.getMessage());
        }
    }

    // =============================================
    // LOAD COURSE OFFERING
    // =============================================

    private record OfferingData(
            List<AttendanceSession> sessions,
            List<AttendanceEnrollment> enrollments
    ) {}

    private void loadOffering(int offeringId) {

        run(
                "Loading course sessions and enrollments...",

                () -> new OfferingData(
                        attendanceService.getSessionsByOffering(offeringId),
                        attendanceService.getEnrollmentsByOffering(offeringId)
                ),

                data -> {

                    sessionCombo.getItems().setAll(
                            data.sessions()
                    );

                    // STEP 19: Refresh session progress
                    updateSessionProgress();

                    enrollmentCombo.getItems().setAll(
                            data.enrollments()
                    );

                    statusLabel.setText(
                            "Choose an existing session or create one below. "
                                    + data.enrollments().size()
                                    + " undergraduate enrollments available."
                    );

                    updateControlState();
                }
        );
    }

    private void selectEnrollment(int enrollmentId) {

        enrollmentCombo.getItems()
                .stream()
                .filter(row ->
                        row.enrollmentId() == enrollmentId
                )
                .findFirst()
                .ifPresent(enrollmentCombo::setValue);
    }

    // =============================================
    // STEP 19: UPDATE SESSION PROGRESS
    // =============================================

    private void updateSessionProgress() {

        if (sessionProgressLabel == null || sessionCombo == null) {
            return;
        }

        long theoryCount = sessionCombo.getItems()
                .stream()
                .filter(session ->
                        "THEORY".equals(session.getSessionType())
                )
                .count();

        long practicalCount = sessionCombo.getItems()
                .stream()
                .filter(session ->
                        "PRACTICAL".equals(session.getSessionType())
                )
                .count();

        sessionProgressLabel.setText(
                "THEORY: " + theoryCount
                        + "/" + MAX_SESSIONS_PER_TYPE
                        + "  |  PRACTICAL: "
                        + practicalCount
                        + "/" + MAX_SESSIONS_PER_TYPE
        );
    }

    // =============================================
    // REQUIRED VALUE VALIDATION
    // =============================================

    private <T> T required(T value, String message) {

        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    // =============================================
    // BACKGROUND DATABASE TASKS
    // =============================================

    private <T> void run(
            String message,
            Callable<T> operation,
            Consumer<T> completed) {

        if (busy) {
            return;
        }

        attendanceService.requireManagementSession();

        busy = true;

        statusLabel.setText(message);

        updateControlState();

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {

            busy = false;

            if (activeTask != task || !isCurrentScreen()) {
                return;
            }

            activeTask = null;

            try {

                attendanceService.requireManagementSession();

                completed.accept(task.getValue());

                updateControlState();

            } catch (SecurityException exception) {

                denyAccess(exception.getMessage());
            }
        });

        task.setOnFailed(event -> {

            busy = false;

            if (activeTask != task || !isCurrentScreen()) {
                return;
            }

            activeTask = null;

            try {

                attendanceService.requireManagementSession();

            } catch (SecurityException exception) {

                denyAccess(exception.getMessage());
                return;
            }

            if (task.getException() instanceof SecurityException) {

                denyAccess(
                        task.getException().getMessage()
                );

            } else {

                Throwable failure = task.getException();

                statusLabel.setText(
                        failure == null || failure.getMessage() == null
                                ? "Attendance operation failed. Please retry."
                                : failure.getMessage()
                );

                updateControlState();
            }
        });

        Thread thread = new Thread(
                task,
                "ftms-attendance"
        );

        thread.setDaemon(true);
        thread.start();
    }

    // =============================================
    // SCREEN SECURITY
    // =============================================

    private boolean isCurrentScreen() {

        return !sceneWasAttached
                || (
                attendanceRoot.getScene() != null
                        && attendanceRoot.getScene().getRoot()
                        == attendanceRoot
        );
    }

    private void denyAccess(String message) {

        clearOfferingData();

        offeringCombo.getItems().clear();

        updateControlState();

        statusLabel.setText(message);
    }

    // =============================================
    // BACK TO DASHBOARD
    // =============================================

    @FXML
    private void handleBack() {

        try {

            attendanceService.requireManagementSession();

            NavigationUtil.navigate(
                    attendanceRoot,
                    NavigationUtil.dashboardResource(),
                    "FTMS - Dashboard"
            );

        } catch (Exception exception) {

            showError(exception.getMessage());
        }
    }

    // =============================================
    // LOGOUT
    // =============================================

    @FXML
    private void handleLogout() {

        try {

            if (SessionManager.isLoggedIn()) {

                attendanceService.requireManagementSession();

                SessionManager.logout();
            }

            denyAccess("You are logged out.");

            NavigationUtil.navigate(
                    attendanceRoot,
                    "/fxml/login.fxml",
                    "FTMS - Login"
            );

        } catch (Exception exception) {

            showError(exception.getMessage());
        }
    }

    // =============================================
    // FORMAT ATTENDANCE PERCENTAGE
    // =============================================

    private static String formatPercentage(Double percentage) {

        if (percentage == null) {
            return "N/A";
        }

        return String.format(
                Locale.ROOT,
                "%.2f%%",
                percentage
        );
    }

    private static String progress(
            int present,
            int sessions,
            Double percentage) {

        return present
                + "/"
                + sessions
                + " ("
                + formatPercentage(percentage)
                + ")";
    }

    // =============================================
    // CLEAR COURSE OFFERING DATA
    // =============================================

    private void clearOfferingData() {

        sessionCombo.setValue(null);
        sessionCombo.getItems().clear();

        enrollmentCombo.setValue(null);
        enrollmentCombo.getItems().clear();

        attendanceStatusCombo.setValue(null);

        attendanceRecords.clear();
        summaryRows.clear();

        summaryDetailsLabel.setText(
                "Select a student to view individual attendance details."
        );

        // STEP 19: Reset progress
        updateSessionProgress();

        updateControlState();
    }

    // =============================================
    // ENABLE / DISABLE BUTTONS
    // =============================================

    private void updateControlState() {

        if (offeringCombo == null) {
            return;
        }

        boolean authenticated;

        try {

            attendanceService.requireManagementSession();
            authenticated = true;

        } catch (SecurityException exception) {

            authenticated = false;
        }

        boolean loading = busy || !authenticated;

        boolean offeringSelected =
                offeringCombo.getValue() != null;

        boolean sessionSelected =
                sessionCombo.getValue() != null;

        boolean enrollmentSelected =
                enrollmentCombo.getValue() != null;

        boolean statusSelected =
                attendanceStatusCombo.getValue() != null;

        boolean sessionInputsValid =
                offeringSelected
                        && sessionDatePicker.getValue() != null
                        && sessionTypeCombo.getValue() != null
                        && validSessionTimes()
                        && (
                        topicField.getText() == null
                                || topicField.getText().length() <= 255
                );

        // STEP 19: Check 15-session limit
        String selectedType = sessionTypeCombo.getValue();

        boolean limitedType =
                "THEORY".equals(selectedType)
                        || "PRACTICAL".equals(selectedType);

        long existingTypeCount = limitedType
                ? sessionCombo.getItems()
                .stream()
                .filter(session ->
                        selectedType.equals(
                                session.getSessionType()
                        )
                )
                .count()
                : 0L;

        boolean selectedTypeFull =
                limitedType
                        && existingTypeCount >= MAX_SESSIONS_PER_TYPE;

        offeringCombo.setDisable(loading);

        saveSessionButton.setDisable(
                loading
                        || !sessionInputsValid
                        || selectedTypeFull
        );

        saveAttendanceButton.setDisable(
                loading
                        || !sessionSelected
                        || !enrollmentSelected
                        || !statusSelected
        );

        loadAttendanceButton.setDisable(
                loading || !sessionSelected
        );

        loadSummaryButton.setDisable(
                loading || !offeringSelected
        );
    }

    // =============================================
    // VALIDATE SESSION TIMES
    // =============================================

    private boolean validSessionTimes() {

        if (!isTime(startTimeField.getText())
                || !isTime(endTimeField.getText())) {

            return false;
        }

        return LocalTime.parse(
                endTimeField.getText().trim()
        ).isAfter(
                LocalTime.parse(
                        startTimeField.getText().trim()
                )
        );
    }

    private boolean isTime(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        try {

            LocalTime.parse(value.trim());
            return true;

        } catch (java.time.format.DateTimeParseException exception) {

            return false;
        }
    }

    // =============================================
    // CLEAR FORM
    // =============================================

    @FXML
    private void handleClear() {

        if (busy) {
            return;
        }

        try {

            attendanceService.requireManagementSession();

            offeringCombo.setValue(null);

            clearOfferingData();

            sessionDatePicker.setValue(LocalDate.now());

            startTimeField.clear();
            endTimeField.clear();

            sessionTypeCombo.setValue(null);

            topicField.clear();

            statusLabel.setText(
                    "Select a course offering from your department to begin."
            );

        } catch (SecurityException exception) {

            denyAccess(exception.getMessage());
        }
    }

    // =============================================
    // ERROR MESSAGE
    // =============================================

    private void showError(String message) {

        statusLabel.setText(
                message == null
                        ? "Attendance operation failed."
                        : message
        );

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Attendance Error");
        alert.setHeaderText(null);
        alert.setContentText(statusLabel.getText());

        if (attendanceRoot.getScene() != null) {

            alert.initOwner(
                    attendanceRoot.getScene().getWindow()
            );
        }

        alert.showAndWait();
    }
}
