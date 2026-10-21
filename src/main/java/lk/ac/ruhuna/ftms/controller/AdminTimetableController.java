
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableInput;
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.AdminTimetableService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Admin-only timetable management.
 * Only authenticated administrators may manage timetable entries.
 */
public final class AdminTimetableController {

    @FXML private BorderPane adminTimetableRoot;

    @FXML private Label adminNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label selectedEntryLabel;
    @FXML private Label statusLabel;

    // Course filters and form

    @FXML private ComboBox<AttendanceOffering> filterOfferingComboBox;
    @FXML private ComboBox<AttendanceOffering> entryOfferingComboBox;

    @FXML private ComboBox<String> dayComboBox;
    @FXML private ComboBox<String> sessionTypeComboBox;

    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private TextField roomField;
    @FXML private TextField lecturerIdField;

    @FXML private DatePicker effectiveFromPicker;
    @FXML private DatePicker effectiveToPicker;

    // Buttons

    @FXML private Button refreshButton;
    @FXML private Button allCoursesButton;
    @FXML private Button backButton;
    @FXML private Button addButton;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Button clearButton;

    // Timetable table

    @FXML private TableView<TimetableEntry> timetableTable;

    @FXML private TableColumn<TimetableEntry, String> idColumn;
    @FXML private TableColumn<TimetableEntry, String> dayColumn;
    @FXML private TableColumn<TimetableEntry, String> timeColumn;
    @FXML private TableColumn<TimetableEntry, String> courseColumn;
    @FXML private TableColumn<TimetableEntry, String> groupColumn;
    @FXML private TableColumn<TimetableEntry, String> sessionTypeColumn;
    @FXML private TableColumn<TimetableEntry, String> roomColumn;
    @FXML private TableColumn<TimetableEntry, String> lecturerColumn;
    @FXML private TableColumn<TimetableEntry, String> effectiveColumn;

    private final ObservableList<TimetableEntry> entries =
            FXCollections.observableArrayList();

    private final SimpleBooleanProperty busy =
            new SimpleBooleanProperty(false);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private SessionIdentity loginIdentity;
    private AdminTimetableService timetableService;

    private boolean changingFilter;

    private record InitialData(
            List<AttendanceOffering> offerings,
            List<TimetableEntry> timetable
    ) {
    }

    // =========================================
    // INITIALIZE
    // =========================================

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        User admin = requireAdmin();

        timetableService = new AdminTimetableService();

        adminNameLabel.setText(
                "Timetable Management - " + admin.getFullName()
        );

        // Day options

        dayComboBox.getItems().setAll(
                "MONDAY",
                "TUESDAY",
                "WEDNESDAY",
                "THURSDAY",
                "FRIDAY",
                "SATURDAY",
                "SUNDAY"
        );

        // Session type options

        sessionTypeComboBox.getItems().setAll(
                "LECTURE",
                "PRACTICAL",
                "TUTORIAL",
                "OTHER"
        );

        sessionTypeComboBox.setValue("LECTURE");

        // Table columns

        idColumn.setCellValueFactory(
                cell -> text(
                        Integer.toString(
                                cell.getValue().timetableId()
                        )
                )
        );

        dayColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().dayOfWeek()
                )
        );

        timeColumn.setCellValueFactory(
                cell -> text(
                        formatTime(cell.getValue())
                )
        );

        courseColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().courseCode()
                                + " - "
                                + cell.getValue().courseName()
                )
        );

        groupColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().offeringGroup()
                )
        );

        sessionTypeColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().sessionType()
                )
        );

        roomColumn.setCellValueFactory(
                cell -> text(
                        orDash(cell.getValue().room())
                )
        );

        lecturerColumn.setCellValueFactory(
                cell -> text(
                        orDash(cell.getValue().lecturerName())
                )
        );

        effectiveColumn.setCellValueFactory(
                cell -> text(
                        formatDates(cell.getValue())
                )
        );

        timetableTable.setItems(entries);

        timetableTable.setPlaceholder(
                new Label("No timetable entries found.")
        );

        selectedEntryLabel.setText(
                "Select a row to edit or delete an entry."
        );

        filterOfferingComboBox.setPromptText(
                "All course offerings"
        );

        entryOfferingComboBox.setPromptText(
                "Select a course offering"
        );

        // Select timetable row for editing

        timetableTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, previous, selected) -> {

                    if (selected != null) {

                        populateForm(selected);

                    } else {

                        selectedEntryLabel.setText(
                                "Select a row to edit or delete an entry."
                        );
                    }
                });

        // Filter by course offering

        filterOfferingComboBox.valueProperty()
                .addListener((obs, previous, selected) -> {

                    if (!changingFilter && !busy.get()) {

                        loadTimetable(selected);
                    }
                });

        // Disable actions during database loading

        refreshButton.disableProperty().bind(busy);
        allCoursesButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);
        addButton.disableProperty().bind(busy);

        updateButton.disableProperty().bind(
                busy.or(
                        timetableTable.getSelectionModel()
                                .selectedItemProperty()
                                .isNull()
                )
        );

        deleteButton.disableProperty().bind(
                busy.or(
                        timetableTable.getSelectionModel()
                                .selectedItemProperty()
                                .isNull()
                )
        );

        clearButton.disableProperty().bind(busy);

        filterOfferingComboBox.disableProperty().bind(busy);
        entryOfferingComboBox.disableProperty().bind(busy);

        dayComboBox.disableProperty().bind(busy);
        sessionTypeComboBox.disableProperty().bind(busy);

        startTimeField.disableProperty().bind(busy);
        endTimeField.disableProperty().bind(busy);
        roomField.disableProperty().bind(busy);
        lecturerIdField.disableProperty().bind(busy);

        effectiveFromPicker.disableProperty().bind(busy);
        effectiveToPicker.disableProperty().bind(busy);

        timetableTable.disableProperty().bind(busy);

        loadInitialData();
    }

    // =========================================
    // ADMIN SESSION VERIFICATION
    // =========================================

    private User requireAdmin() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active Admin session is required."
            );
        }

        return user;
    }

    // =========================================
    // LOAD INITIAL DATA
    // =========================================

    private void loadInitialData() {

        runTask(
                "Loading course offerings and timetable...",

                () -> new InitialData(
                        timetableService.getAllOfferings(),
                        timetableService.getAllTimetable()
                ),

                data -> {

                    changingFilter = true;

                    try {

                        filterOfferingComboBox.getItems()
                                .setAll(data.offerings());

                        filterOfferingComboBox
                                .getSelectionModel()
                                .clearSelection();

                        entryOfferingComboBox.getItems()
                                .setAll(data.offerings());

                    } finally {

                        changingFilter = false;
                    }

                    timetableTable.getSelectionModel()
                            .clearSelection();

                    entries.setAll(data.timetable());

                    clearFormFields();

                    updateSummary(
                            data.timetable(),
                            null
                    );

                    statusLabel.setText(
                            data.timetable().size()
                                    + " timetable entries loaded."
                    );
                }
        );
    }

    // =========================================
    // REFRESH TIMETABLE
    // =========================================

    @FXML
    private void handleRefresh() {

        loadTimetable(
                filterOfferingComboBox.getValue()
        );
    }

    // =========================================
    // DISPLAY ALL COURSES
    // =========================================

    @FXML
    private void handleAllCourses() {

        changingFilter = true;

        try {

            filterOfferingComboBox.getSelectionModel()
                    .clearSelection();

        } finally {

            changingFilter = false;
        }

        loadTimetable(null);
    }

    // =========================================
    // LOAD TIMETABLE
    // =========================================

    private void loadTimetable(
            AttendanceOffering offering) {

        runTask(
                "Loading timetable...",

                () -> offering == null
                        ? timetableService.getAllTimetable()
                        : timetableService.getOfferingTimetable(
                        offering.offeringId()
                ),

                result -> {

                    timetableTable.getSelectionModel()
                            .clearSelection();

                    entries.setAll(result);

                    updateSummary(result, offering);

                    statusLabel.setText(
                            result.size()
                                    + " timetable entries loaded."
                    );
                }
        );
    }

    // =========================================
    // READ FORM INPUT
    // =========================================

    private TimetableInput readInput() {

        AttendanceOffering offering =
                entryOfferingComboBox.getValue();

        if (offering == null) {

            throw new IllegalArgumentException(
                    "Select a course offering."
            );
        }

        if (dayComboBox.getValue() == null) {

            throw new IllegalArgumentException(
                    "Select a day of the week."
            );
        }

        if (sessionTypeComboBox.getValue() == null) {

            throw new IllegalArgumentException(
                    "Select a session type."
            );
        }

        LocalTime start = parseTime(
                startTimeField.getText(),
                "Start time"
        );

        LocalTime end = parseTime(
                endTimeField.getText(),
                "End time"
        );

        if (!start.isBefore(end)) {

            throw new IllegalArgumentException(
                    "Start time must be earlier than end time."
            );
        }

        String room = roomField.getText() == null
                ? ""
                : roomField.getText().strip();

        if (room.length() > 80) {

            throw new IllegalArgumentException(
                    "Room must be at most 80 characters."
            );
        }

        // Lecturer ID is optional

        Integer lecturerId = null;

        String lecturerText =
                lecturerIdField.getText() == null
                        ? ""
                        : lecturerIdField.getText().strip();

        if (!lecturerText.isEmpty()) {

            try {

                lecturerId = Integer.valueOf(lecturerText);

            } catch (NumberFormatException error) {

                throw new IllegalArgumentException(
                        "Lecturer ID must be a positive whole number."
                );
            }

            if (lecturerId <= 0) {

                throw new IllegalArgumentException(
                        "Lecturer ID must be a positive whole number."
                );
            }
        }

        LocalDate from = effectiveFromPicker.getValue();
        LocalDate to = effectiveToPicker.getValue();

        if (from != null
                && to != null
                && from.isAfter(to)) {

            throw new IllegalArgumentException(
                    "Effective-from date cannot be after effective-to date."
            );
        }

        return new TimetableInput(
                offering.offeringId(),
                dayComboBox.getValue(),
                start,
                end,
                room.isEmpty() ? null : room,
                sessionTypeComboBox.getValue(),
                lecturerId,
                from,
                to
        );
    }

    // Validate time format

    private static LocalTime parseTime(
            String input,
            String fieldName) {

        if (input == null || input.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " is required (HH:mm, for example 09:00)."
            );
        }

        try {

            return LocalTime.parse(
                    input.strip(),
                    TIME_FORMAT
            );

        } catch (DateTimeParseException error) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must use 24-hour HH:mm (e.g. 09:00).",
                    error
            );
        }
    }

    // =========================================
    // ADD TIMETABLE ENTRY
    // =========================================

    @FXML
    private void handleAdd() {

        try {

            requireAdmin();

            TimetableInput input = readInput();

            runTask(
                    "Creating timetable entry...",

                    () -> timetableService
                            .createTimetableEntry(input),

                    generatedId -> {

                        showInfo(
                                "Timetable entry #"
                                        + generatedId
                                        + " created."
                        );

                        timetableTable.getSelectionModel()
                                .clearSelection();

                        clearFormFields();

                        loadTimetable(
                                filterOfferingComboBox.getValue()
                        );
                    }
            );

        } catch (IllegalArgumentException error) {

            showValidationError(error.getMessage());

        } catch (SecurityException error) {

            handleAccessDenied(error);
        }
    }

    // =========================================
    // UPDATE TIMETABLE ENTRY
    // =========================================

    @FXML
    private void handleUpdate() {

        try {

            requireAdmin();

            TimetableEntry selected = selectedEntry();

            TimetableInput input = readInput();

            if (!confirm(
                    "Update timetable entry #"
                            + selected.timetableId()
                            + "?")) {

                return;
            }

            runTask(
                    "Updating timetable entry...",

                    () -> {

                        timetableService.updateTimetableEntry(
                                selected.timetableId(),
                                input
                        );

                        return null;
                    },

                    ignored -> {

                        showInfo("Timetable entry updated.");

                        timetableTable.getSelectionModel()
                                .clearSelection();

                        loadTimetable(
                                filterOfferingComboBox.getValue()
                        );
                    }
            );

        } catch (IllegalArgumentException error) {

            showValidationError(error.getMessage());

        } catch (SecurityException error) {

            handleAccessDenied(error);
        }
    }

    // =========================================
    // DELETE TIMETABLE ENTRY
    // =========================================

    @FXML
    private void handleDelete() {

        try {

            requireAdmin();

            TimetableEntry selected = selectedEntry();

            if (!confirm(
                    "Delete timetable entry #"
                            + selected.timetableId()
                            + "? This cannot be undone.")) {

                return;
            }

            runTask(
                    "Deleting timetable entry...",

                    () -> {

                        timetableService.deleteTimetableEntry(
                                selected.timetableId()
                        );

                        return null;
                    },

                    ignored -> {

                        showInfo("Timetable entry deleted.");

                        timetableTable.getSelectionModel()
                                .clearSelection();

                        clearFormFields();

                        loadTimetable(
                                filterOfferingComboBox.getValue()
                        );
                    }
            );

        } catch (IllegalArgumentException error) {

            showValidationError(error.getMessage());

        } catch (SecurityException error) {

            handleAccessDenied(error);
        }
    }

    // =========================================
    // SELECT TIMETABLE ROW
    // =========================================

    private TimetableEntry selectedEntry() {

        TimetableEntry row =
                timetableTable.getSelectionModel()
                        .getSelectedItem();

        if (row == null) {

            throw new IllegalArgumentException(
                    "Select a timetable row before updating or deleting."
            );
        }

        return row;
    }

    // Populate form from selected timetable row

    private void populateForm(TimetableEntry row) {

        entryOfferingComboBox.getItems()
                .stream()
                .filter(offering ->
                        offering.offeringId() == row.offeringId()
                )
                .findFirst()
                .ifPresent(entryOfferingComboBox::setValue);

        dayComboBox.setValue(row.dayOfWeek());

        sessionTypeComboBox.setValue(row.sessionType());

        startTimeField.setText(
                row.startTime() == null
                        ? ""
                        : row.startTime().format(TIME_FORMAT)
        );

        endTimeField.setText(
                row.endTime() == null
                        ? ""
                        : row.endTime().format(TIME_FORMAT)
        );

        roomField.setText(
                row.room() == null ? "" : row.room()
        );

        lecturerIdField.setText(
                row.lecturerId() == null
                        ? ""
                        : row.lecturerId().toString()
        );

        effectiveFromPicker.setValue(
                row.effectiveFrom()
        );

        effectiveToPicker.setValue(
                row.effectiveTo()
        );

        selectedEntryLabel.setText(
                "Selected timetable ID: " + row.timetableId()
        );
    }

    // =========================================
    // CLEAR FORM
    // =========================================

    @FXML
    private void handleClear() {

        try {

            requireAdmin();

            timetableTable.getSelectionModel()
                    .clearSelection();

            clearFormFields();

            statusLabel.setText("Entry form cleared.");

        } catch (SecurityException error) {

            handleAccessDenied(error);
        }
    }

    private void clearFormFields() {

        entryOfferingComboBox.getSelectionModel()
                .clearSelection();

        dayComboBox.getSelectionModel()
                .clearSelection();

        sessionTypeComboBox.setValue("LECTURE");

        startTimeField.clear();
        endTimeField.clear();
        roomField.clear();
        lecturerIdField.clear();

        effectiveFromPicker.setValue(null);
        effectiveToPicker.setValue(null);

        selectedEntryLabel.setText(
                "Select a row to edit or delete an entry."
        );
    }

    // =========================================
    // TIMETABLE SUMMARY
    // =========================================

    private void updateSummary(
            List<TimetableEntry> result,
            AttendanceOffering filter) {

        long courses = result.stream()
                .map(TimetableEntry::offeringId)
                .distinct()
                .count();

        String scope = filter == null
                ? "All Departments"
                : filter.courseCode()
                + " (Offering "
                + filter.offeringId()
                + ")";

        summaryLabel.setText(
                scope
                        + " | Courses: "
                        + courses
                        + " | Entries: "
                        + result.size()
        );
    }

    // =========================================
    // BACK TO ADMIN DASHBOARD
    // =========================================

    @FXML
    private void handleBack() {

        try {

            requireAdmin();

            NavigationUtil.navigate(
                    adminTimetableRoot,
                    "/fxml/admin_dashboard.fxml",
                    "FTMS - Admin Dashboard"
            );

        } catch (IOException | RuntimeException error) {

            showError(
                    "Could not open Admin Dashboard.",
                    error
            );
        }
    }

    // =========================================
    // BACKGROUND DATABASE TASK
    // =========================================

    private <T> void runTask(
            String message,
            Callable<T> databaseWork,
            Consumer<T> onSuccess) {

        if (busy.get()) {
            return;
        }

        try {

            requireAdmin();

        } catch (SecurityException error) {

            handleAccessDenied(error);
            return;
        }

        busy.set(true);

        statusLabel.setText(message);

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {

                return databaseWork.call();
            }
        };

        task.setOnSucceeded(event -> {

            busy.set(false);

            try {

                requireAdmin();

                onSuccess.accept(task.getValue());

            } catch (SecurityException error) {

                handleAccessDenied(error);

            } catch (RuntimeException error) {

                showError(
                        "Could not display timetable results.",
                        error
                );
            }
        });

        task.setOnFailed(event -> {

            busy.set(false);

            Throwable error = task.getException();

            if (error instanceof SecurityException) {

                handleAccessDenied(error);

            } else if (error instanceof IllegalArgumentException) {

                showValidationError(error.getMessage());

            } else {

                showError(
                        "Timetable operation failed. See console for details.",
                        error
                );
            }
        });

        Thread thread = new Thread(
                task,
                "ftms-admin-timetable"
        );

        thread.setDaemon(true);
        thread.start();
    }

    // =========================================
    // ACCESS DENIED
    // =========================================

    private void handleAccessDenied(Throwable error) {

        entries.clear();

        timetableTable.getSelectionModel()
                .clearSelection();

        filterOfferingComboBox.getItems().clear();
        entryOfferingComboBox.getItems().clear();

        showError(
                "Admin session expired or access was denied. Please log in again.",
                error
        );
    }

    // =========================================
    // CONFIRMATION AND MESSAGES
    // =========================================

    private boolean confirm(String message) {

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                message,
                ButtonType.YES,
                ButtonType.NO
        );

        alert.setTitle("Confirm Timetable Change");
        alert.setHeaderText(null);

        setOwner(alert);

        return alert.showAndWait()
                .filter(ButtonType.YES::equals)
                .isPresent();
    }

    private void showInfo(String message) {

        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK
        );

        alert.setTitle("Admin Timetable");
        alert.setHeaderText(null);

        setOwner(alert);
        alert.showAndWait();
    }

    private void showValidationError(String message) {

        Alert alert = new Alert(
                Alert.AlertType.WARNING,
                message,
                ButtonType.OK
        );

        alert.setTitle("Check Timetable Details");
        alert.setHeaderText(null);

        statusLabel.setText(message);

        setOwner(alert);
        alert.showAndWait();
    }

    private void showError(
            String message,
            Throwable error) {

        statusLabel.setText(message);

        if (error != null) {

            error.printStackTrace();
        }

        Alert alert = new Alert(
                Alert.AlertType.ERROR,
                message,
                ButtonType.OK
        );

        alert.setTitle("Admin Timetable");
        alert.setHeaderText(null);

        setOwner(alert);
        alert.showAndWait();
    }

    private void setOwner(Alert alert) {

        if (adminTimetableRoot.getScene() != null
                && adminTimetableRoot.getScene()
                .getWindow() != null) {

            alert.initOwner(
                    adminTimetableRoot.getScene().getWindow()
            );
        }
    }

    // =========================================
    // FORMATTING HELPERS
    // =========================================

    private static ReadOnlyStringWrapper text(
            String value) {

        return new ReadOnlyStringWrapper(
                value == null ? "" : value
        );
    }

    private static String orDash(String value) {

        return value == null || value.isBlank()
                ? "-"
                : value;
    }

    private static String formatTime(TimetableEntry row) {

        String from = row.startTime() == null
                ? "-"
                : row.startTime().format(TIME_FORMAT);

        String to = row.endTime() == null
                ? "-"
                : row.endTime().format(TIME_FORMAT);

        return from + " - " + to;
    }

    private static String formatDates(TimetableEntry row) {

        String from = row.effectiveFrom() == null
                ? "Open"
                : row.effectiveFrom().format(DATE_FORMAT);

        String to = row.effectiveTo() == null
                ? "Open"
                : row.effectiveTo().format(DATE_FORMAT);

        return from + " to " + to;
    }
}
