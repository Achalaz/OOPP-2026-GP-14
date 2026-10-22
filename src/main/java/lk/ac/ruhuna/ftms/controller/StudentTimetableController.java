package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
    private SessionIdentity loginIdentity;
    private StudentTimetableService timetableService;

    @FXML
    public void initialize() {
        loginIdentity = SessionManager.requireSession();
        User student = requireStudent();
        timetableService = new StudentTimetableService();

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
                new Label("No timetable entries for the selected day."));

        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);
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
        if (busy.get()) {
            return;
        }
        try {
            requireStudent();
        } catch (SecurityException error) {
            showError(error.getMessage());
            return;
        }

        busy.set(true);
        statusLabel.setText("Loading your enrolled course timetable...");

        Task<List<TimetableEntry>> task = new Task<>() {
            @Override
            protected List<TimetableEntry> call() {
                return timetableService.getMyTimetable();
            }
        };

        task.setOnSucceeded(event -> {
            busy.set(false);
            try {
                requireStudent();
                allEntries = task.getValue();
                showFilteredEntries();
                statusLabel.setText("Timetable loaded successfully.");
            } catch (SecurityException error) {
                allEntries = List.of();
                displayedRows.clear();
                showError(error.getMessage());
            }
        });

        task.setOnFailed(event -> {
            busy.set(false);
            allEntries = List.of();
            displayedRows.clear();
            Throwable error = task.getException();
            if (error != null) {
                error.printStackTrace();
            }
            showError(error instanceof SecurityException
                    ? error.getMessage()
                    : "Could not load your timetable. Check the console.");
        });

        Thread worker = new Thread(task, "ftms-student-timetable");
        worker.setDaemon(true);
        worker.start();
    }

    private void showFilteredEntries() {
        String day = dayFilterComboBox.getValue();
        List<TimetableEntry> selected = allEntries.stream()
                .filter(entry -> day == null || "All Days".equals(day)
                        || day.equals(entry.dayOfWeek()))
                .toList();
        displayedRows.setAll(selected);

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
            showError("Could not return to Student Dashboard.");
        }
    }

    private void showError(String message) {
        statusLabel.setText(message);
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
