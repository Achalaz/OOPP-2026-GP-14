package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.StudentTimetableService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Student timetable: enrolled courses only, no write actions. */
public final class StudentTimetableController {

    @FXML private BorderPane studentTimetableRoot;
    @FXML private Label studentNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> dayFilterComboBox;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private TableView<TimetableEntry> timetableTable;
    @FXML private TableColumn<TimetableEntry, String> dayColumn;
    @FXML private TableColumn<TimetableEntry, String> timeColumn;
    @FXML private TableColumn<TimetableEntry, String> courseColumn;
    @FXML private TableColumn<TimetableEntry, String> groupColumn;
    @FXML private TableColumn<TimetableEntry, String> typeColumn;
    @FXML private TableColumn<TimetableEntry, String> roomColumn;
    @FXML private TableColumn<TimetableEntry, String> lecturerColumn;
    @FXML private TableColumn<TimetableEntry, String> dateColumn;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private final ObservableList<TimetableEntry> displayedRows =
            FXCollections.observableArrayList();
    private final SimpleBooleanProperty busy =
            new SimpleBooleanProperty(false);

    private List<TimetableEntry> allEntries = List.of();
    private int enrolledOfferingCount;
    private boolean loaded;
    private SessionIdentity loginIdentity;
    private StudentTimetableService timetableService;
    private TimetableTaskRunner tasks;

    @FXML
    public void initialize() {
        loginIdentity = SessionManager.requireSession();
        User student = requireStudent();
        timetableService = new StudentTimetableService();
        tasks = new TimetableTaskRunner(studentTimetableRoot, busy, statusLabel, loginIdentity, this::clearData);

        studentNameLabel.setText("My Timetable — " + student.getFullName());

        dayFilterComboBox.getItems().setAll(
                "All Days", "MONDAY", "TUESDAY", "WEDNESDAY",
                "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        dayFilterComboBox.setValue("All Days");

        dayColumn.setCellValueFactory(cell ->
                value(cell.getValue().dayOfWeek()));
        timeColumn.setCellValueFactory(cell ->
                value(timeText(cell.getValue())));
        courseColumn.setCellValueFactory(cell ->
                value(cell.getValue().courseCode() + " - "
                        + cell.getValue().courseName()));
        groupColumn.setCellValueFactory(cell ->
                value(cell.getValue().offeringGroup()));
        typeColumn.setCellValueFactory(cell ->
                value(cell.getValue().sessionType()));
        roomColumn.setCellValueFactory(cell ->
                value(orDash(cell.getValue().room())));
        lecturerColumn.setCellValueFactory(cell ->
                value(orDash(cell.getValue().lecturerName())));
        dateColumn.setCellValueFactory(cell ->
                value(datesText(cell.getValue())));

        timetableTable.setItems(displayedRows);
        timetableTable.setPlaceholder(
                new Label("Loading your course timetable..."));

        refreshButton.disableProperty().bind(busy);
        dayFilterComboBox.disableProperty().bind(busy);

        dayFilterComboBox.valueProperty().addListener(
                (observable, oldDay, newDay) -> showFilteredEntries());

        refreshTimetable();
    }

    private User requireStudent() {
        SessionManager.requireSession(loginIdentity);
        User student = SessionManager.getCurrentUser();
        if (student == null || !student.isActive()
                || !"STUDENT".equals(student.getRole())
                || student.getUserId() != loginIdentity.userId()) {
            throw new SecurityException("An active student session is required.");
        }
        return student;
    }

    @FXML
    private void handleRefresh() {
        refreshTimetable();
    }

    private void refreshTimetable() {
        tasks.run("Loading your enrollments and timetable...", timetableService::getMyTimetableData, data -> {
            User student = requireStudent();
            studentNameLabel.setText("My Timetable — " + student.getFullName());
            enrolledOfferingCount = data.enrolledOfferingCount();
            allEntries = data.entries();
            loaded = true;
            showFilteredEntries();
        }, error -> {
            clearData();
            showError(TimetableTaskRunner.errorMessage("Could not load your timetable.", error));
        });
    }

    private void clearData() {
        loaded = false;
        enrolledOfferingCount = 0;
        allEntries = List.of();
        displayedRows.clear();
        studentNameLabel.setText("My Timetable");
        summaryLabel.setText("Timetable unavailable.");
        timetableTable.setPlaceholder(new Label("Timetable is unavailable. See the message below for the next step."));
    }

    private void showFilteredEntries() {
        try {
            requireStudent();
        } catch (SecurityException error) {
            clearData();
            statusLabel.setText(error.getMessage());
            return;
        }
        if (!loaded) return;
        String day = dayFilterComboBox.getValue();
        List<TimetableEntry> selected = allEntries.stream()
                .filter(entry -> day == null || "All Days".equals(day)
                        || day.equals(entry.dayOfWeek()))
                .toList();
        displayedRows.setAll(selected);
        String dayName = day == null || "All Days".equals(day) ? "all days"
                : day.substring(0, 1) + day.substring(1).toLowerCase(Locale.ROOT);
        String message;
        if (enrolledOfferingCount == 0) {
            message = "You have no enrolled or repeat course offerings. Check My Courses and contact your department if an enrollment is missing.";
        } else if (allEntries.isEmpty()) {
            message = "No timetable entries are available for your enrolled or repeat course offerings. Contact your department or click Refresh later.";
        } else if (selected.isEmpty()) {
            message = "No sessions are scheduled for " + dayName + ". Select All Days to view your full timetable.";
        } else {
            message = "Showing " + selected.size() + (selected.size() == 1 ? " session" : " sessions") + " for " + dayName + ".";
        }
        Label emptyMessage = new Label(message);
        emptyMessage.setWrapText(true);
        emptyMessage.setMaxWidth(420);
        timetableTable.setPlaceholder(emptyMessage);
        statusLabel.setText(message);

        long offerings = selected.stream()
                .map(TimetableEntry::offeringId)
                .distinct()
                .count();
        summaryLabel.setText("Entries: " + selected.size()
                + "   |   Courses: " + offerings
                + "   |   Day: " + (day == null ? "All Days" : day));
    }

    @FXML
    private void handleBack() {
        try {
            requireStudent();
            NavigationUtil.navigate(
                    studentTimetableRoot,
                    "/fxml/student_dashboard.fxml",
                    "FTMS - Student Dashboard");
        } catch (IOException | RuntimeException error) {
            error.printStackTrace();
            showError(TimetableTaskRunner.errorMessage("Could not open the Student Dashboard. Try Back to Dashboard again.", error));
        }
    }

    private void showError(String message) {
        if (!SessionManager.isCurrentSession(loginIdentity)) clearData();
        statusLabel.setText(TimetableTaskRunner.statusMessage(message));
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Student Timetable");
        alert.setHeaderText(null);
        if (studentTimetableRoot.getScene() != null
                && studentTimetableRoot.getScene().getWindow() != null) {
            alert.initOwner(studentTimetableRoot.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static ReadOnlyStringWrapper value(String text) {
        return new ReadOnlyStringWrapper(text == null ? "" : text);
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String timeText(TimetableEntry entry) {
        return formatTime(entry.startTime()) + " - "
                + formatTime(entry.endTime());
    }

    private static String formatTime(LocalTime time) {
        return time == null ? "-" : time.format(TIME_FORMAT);
    }

    private static String datesText(TimetableEntry entry) {
        return formatDate(entry.effectiveFrom()) + " to "
                + formatDate(entry.effectiveTo());
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "Open" : date.format(DATE_FORMAT);
    }
}
