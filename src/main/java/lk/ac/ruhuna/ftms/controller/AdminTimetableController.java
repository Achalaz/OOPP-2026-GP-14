
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import lk.ac.ruhuna.ftms.dao.TimetableDAO.AssignedLecturer;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;
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
    @FXML private ComboBox<AssignedLecturer> lecturerComboBox;

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
    private TimetableTaskRunner tasks;
    private List<AssignedLecturer> assignedLecturers = List.of();
    private static final AssignedLecturer UNASSIGNED = new AssignedLecturer(0, 0, "Unassigned");

    private record InitialData(
            List<AttendanceOffering> offerings,
            List<AttendanceOffering> schedulableOfferings,
            List<AssignedLecturer> lecturers,
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
        tasks = new TimetableTaskRunner(adminTimetableRoot, busy, statusLabel, loginIdentity, this::clearData);
        entryOfferingComboBox.valueProperty().addListener((obs, oldValue, offering) -> updateLecturers(offering));
        for (DatePicker picker : List.of(effectiveFromPicker, effectiveToPicker)) {
            picker.setPromptText("yyyy-MM-dd (optional)");
            picker.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(LocalDate date) {
                    return date == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE.format(date);
                }
                @Override public LocalDate fromString(String text) {
                    return text == null || text.isBlank() ? null
                            : LocalDate.parse(text.strip(), DateTimeFormatter.ISO_LOCAL_DATE);
                }
            });
        }

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
        lecturerComboBox.disableProperty().bind(busy);

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
        AttendanceOffering previousFilter = filterOfferingComboBox.getValue();

        runTask(
                "Loading course offerings and timetable...",

                () -> new InitialData(
                        timetableService.getAllOfferings(),
                        timetableService.getSchedulableOfferings(),
                        timetableService.getAssignedLecturers(),
                        timetableService.getAllTimetable()
                ),

                data -> {

                    assignedLecturers = data.lecturers();
                    changingFilter = true;

                    try {

                        filterOfferingComboBox.getItems()
                                .setAll(data.offerings());

                        filterOfferingComboBox.setValue(previousFilter == null ? null :
                                data.offerings().stream().filter(o -> o.offeringId() == previousFilter.offeringId())
                                        .findFirst().orElse(null));

                        entryOfferingComboBox.getItems()
                                .setAll(data.schedulableOfferings());

                    } finally {

                        changingFilter = false;
                    }

                    timetableTable.getSelectionModel()
                            .clearSelection();

                    AttendanceOffering filter = filterOfferingComboBox.getValue();
                    entries.setAll(data.timetable().stream().filter(row -> filter == null
                            || row.offeringId() == filter.offeringId()).toList());

                    clearFormFields();

                    updateSummary(
                            List.copyOf(entries),
                            filter
                    );

                    showLoadedMessage(entries.size(), filter);
                },
                true
        );
    }

    // =========================================
    // REFRESH TIMETABLE
    // =========================================

    @FXML
    private void handleRefresh() {
        loadInitialData();
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
                    clearFormFields();

                    updateSummary(result, offering);

                    showLoadedMessage(result.size(), offering);
                },
                true
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

        AssignedLecturer lecturer = lecturerComboBox.getValue();
        Integer lecturerId = lecturer == null || lecturer.lecturerId() == 0 ? null : lecturer.lecturerId();
        if (lecturerId != null && assignedLecturers.stream().noneMatch(a ->
                a.offeringId() == offering.offeringId() && a.lecturerId() == lecturerId)) {
            throw new IllegalArgumentException("Select an active lecturer assigned to this offering, or Unassigned.");
        }

        // Commit typed dates too; DatePicker.getValue alone can silently use an older date.
        commitDate(effectiveFromPicker, "Effective-from");
        commitDate(effectiveToPicker, "Effective-to");
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

    private static void commitDate(DatePicker picker, String field) {
        String text = picker.getEditor().getText();
        try {
            picker.setValue(text == null || text.isBlank() ? null : picker.getConverter().fromString(text));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " must be a valid date in yyyy-MM-dd format, for example 2026-10-22. Leave it blank for an open date range.", error);
        }
    }

    private void updateLecturers(AttendanceOffering offering) {
        lecturerComboBox.getItems().setAll(UNASSIGNED);
        if (offering != null) lecturerComboBox.getItems().addAll(assignedLecturers.stream()
                .filter(a -> a.offeringId() == offering.offeringId()).toList());
        lecturerComboBox.setValue(UNASSIGNED);
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
                                        + " created.\n\n" + savedEntryDetails(input)
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
                            + " for " + selected.courseCode() + "?\n\nNew schedule: " + savedEntryDetails(input))) {
                statusLabel.setText("Update cancelled. The timetable entry was not changed.");
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

                        showInfo("Timetable entry #" + selected.timetableId() + " updated.\n\n" + savedEntryDetails(input));

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
                            + " for " + selected.courseCode() + "?\n"
                            + selected.dayOfWeek() + ", " + formatTime(selected)
                            + "\n\nThis removes this scheduled session. This cannot be undone.")) {
                statusLabel.setText("Deletion cancelled. The timetable entry was not removed.");
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

                        showInfo("Timetable entry #" + selected.timetableId() + " for " + selected.courseCode()
                                + " deleted.\n" + selected.dayOfWeek() + ", " + formatTime(selected));

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
                .ifPresentOrElse(entryOfferingComboBox::setValue,
                        () -> entryOfferingComboBox.setValue(new AttendanceOffering(row.offeringId(),
                                row.courseCode(), row.courseName(), row.semesterId(), row.offeringGroup())));

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

        lecturerComboBox.setValue(row.lecturerId() == null ? UNASSIGNED :
                assignedLecturers.stream().filter(a -> a.offeringId() == row.offeringId()
                        && a.lecturerId() == row.lecturerId()).findFirst()
                        .orElse(new AssignedLecturer(row.offeringId(), row.lecturerId(),
                                orDash(row.lecturerName()) + " (no longer assigned)")));

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
        lecturerComboBox.setValue(UNASSIGNED);

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

    private void showLoadedMessage(int count, AttendanceOffering offering) {
        String scope = offering == null ? "all course offerings"
                : offering.courseCode() + " (offering #" + offering.offeringId() + ")";
        String message;
        if (filterOfferingComboBox.getItems().isEmpty()) {
            message = "No course offerings are available. Create one in Course Offerings, then click Refresh.";
        } else if (count == 0) {
            message = "No timetable entries found for " + scope + ". "
                    + (entryOfferingComboBox.getItems().isEmpty()
                    ? "No offerings are available for scheduling. Check Course Offerings, then click Refresh."
                    : offering != null && entryOfferingComboBox.getItems().stream()
                            .noneMatch(o -> o.offeringId() == offering.offeringId())
                            ? "This offering is unavailable for scheduling. Choose another offering."
                            : "Use the entry form below to add a session.")
                    + (offering == null ? "" : " Choose All Courses to check other offerings.");
        } else {
            message = "Showing " + count + (count == 1 ? " timetable entry" : " timetable entries") + " for " + scope + ".";
        }
        Label placeholder = new Label(message);
        placeholder.setWrapText(true);
        placeholder.setMaxWidth(420);
        timetableTable.setPlaceholder(placeholder);
        statusLabel.setText(message);
    }

    private String savedEntryDetails(TimetableInput input) {
        String offering = entryOfferingComboBox.getItems().stream()
                .filter(o -> o.offeringId() == input.offeringId()).map(AttendanceOffering::toString)
                .findFirst().orElse("Course offering #" + input.offeringId());
        AttendanceOffering filter = filterOfferingComboBox.getValue();
        return offering + "\n" + input.dayOfWeek() + ", " + input.startTime().format(TIME_FORMAT)
                + " - " + input.endTime().format(TIME_FORMAT)
                + (filter != null && filter.offeringId() != input.offeringId()
                ? "\n\nThis session is outside the current course filter. Choose All Courses to view it." : "");
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

    private <T> void runTask(String message, Callable<T> databaseWork, Consumer<T> onSuccess) {
        runTask(message, databaseWork, onSuccess, false);
    }

    private <T> void runTask(String message, Callable<T> databaseWork, Consumer<T> onSuccess,
                             boolean clearOnFailure) {
        tasks.run(message, () -> {
            requireAdmin();
            return databaseWork.call();
        }, result -> {
            requireAdmin();
            onSuccess.accept(result);
        }, error -> {
            // Failed reads must not leave rows from a previous course filter editable.
            // Failed writes retain the form so the administrator can correct or retry it.
            if (clearOnFailure) clearData();
            if (error instanceof SecurityException) handleAccessDenied(error);
            else if (error instanceof IllegalArgumentException) showValidationError(error.getMessage());
            else showError(message.replace("...", "") + " could not be completed.", error);
        });
    }

    private void clearData() {
        changingFilter = true;
        try {
            entries.clear();
            filterOfferingComboBox.getItems().clear();
            entryOfferingComboBox.getItems().clear();
            assignedLecturers = List.of();
            clearFormFields();
            summaryLabel.setText("Timetable unavailable.");
            timetableTable.setPlaceholder(new Label("Timetable is unavailable. See the message below for the next step."));
        } finally { changingFilter = false; }
    }

    // =========================================
    // ACCESS DENIED
    // =========================================

    private void handleAccessDenied(Throwable error) {

        clearData();

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

        statusLabel.setText(message.lines().findFirst().orElse(message));

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

        message = TimetableTaskRunner.errorMessage(message, error);
        statusLabel.setText(TimetableTaskRunner.statusMessage(message));
        if (summaryLabel.getText().startsWith("Loading")) summaryLabel.setText("Timetable unavailable.");

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
