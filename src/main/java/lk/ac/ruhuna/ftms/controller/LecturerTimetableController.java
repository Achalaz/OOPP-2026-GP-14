package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.LecturerTimetableService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Lecturer timetable display; all authorization and assignment filtering happen in the service/DAO. */
public final class LecturerTimetableController {
    @FXML private BorderPane lecturerTimetableRoot;
    @FXML private Label lecturerNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private TableView<TimetableEntry> timetableTable;
    @FXML private TableColumn<TimetableEntry, String> dayColumn;
    @FXML private TableColumn<TimetableEntry, String> startColumn;
    @FXML private TableColumn<TimetableEntry, String> endColumn;
    @FXML private TableColumn<TimetableEntry, String> courseColumn;
    @FXML private TableColumn<TimetableEntry, String> groupColumn;
    @FXML private TableColumn<TimetableEntry, String> typeColumn;
    @FXML private TableColumn<TimetableEntry, String> roomColumn;
    @FXML private TableColumn<TimetableEntry, String> dateColumn;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final String EMPTY_MESSAGE =
            "You have no assigned timetable entries. Contact your department or click Refresh later.";
    private final ObservableList<TimetableEntry> entries = FXCollections.observableArrayList();
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
    private SessionIdentity loginIdentity;
    private LecturerTimetableService timetableService;
    private TimetableTaskRunner tasks;

    public LecturerTimetableController() {}

    // Allows the JavaFX acceptance runner to use connection-local database fixtures.
    LecturerTimetableController(LecturerTimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @FXML
    public void initialize() {
        loginIdentity = SessionManager.requireSession();
        User lecturer = requireLecturer();
        if (timetableService == null) timetableService = new LecturerTimetableService();
        tasks = new TimetableTaskRunner(lecturerTimetableRoot, busy, statusLabel, loginIdentity, this::clearData);
        lecturerNameLabel.setText(lecturer.getFullName());

        dayColumn.setCellValueFactory(cell -> text(cell.getValue().dayOfWeek()));
        startColumn.setCellValueFactory(cell -> text(time(cell.getValue().startTime())));
        endColumn.setCellValueFactory(cell -> text(time(cell.getValue().endTime())));
        courseColumn.setCellValueFactory(cell -> text(cell.getValue().courseCode() + " - " + cell.getValue().courseName()));
        groupColumn.setCellValueFactory(cell -> text(cell.getValue().offeringGroup()));
        typeColumn.setCellValueFactory(cell -> text(cell.getValue().sessionType()));
        roomColumn.setCellValueFactory(cell -> text(cell.getValue().room()));
        dateColumn.setCellValueFactory(cell -> text(dates(cell.getValue())));
        timetableTable.setItems(entries);
        timetableTable.setEditable(false);
        placeholder("Loading your assigned timetable...");
        refreshButton.disableProperty().bind(busy);
        handleRefresh();
    }

    private User requireLecturer() {
        SessionManager.requireSession(loginIdentity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"LECTURER".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {
            throw new SecurityException("An active lecturer session is required.");
        }
        return user;
    }

    @FXML
    private void handleRefresh() {
        tasks.run("Loading your assigned timetable...", timetableService::getMyTimetable, this::showEntries,
                error -> {
                    clearData();
                    showError(TimetableTaskRunner.errorMessage("Could not load your timetable.", error));
                });
    }

    private void showEntries(List<TimetableEntry> rows) {
        lecturerNameLabel.setText(requireLecturer().getFullName());
        entries.setAll(rows);
        summaryLabel.setText("Entries: " + rows.size() + "   |   Course offerings: "
                + rows.stream().map(TimetableEntry::offeringId).distinct().count());
        String message = rows.isEmpty() ? EMPTY_MESSAGE
                : "Showing " + rows.size() + (rows.size() == 1 ? " assigned session." : " assigned sessions.");
        placeholder(message);
        statusLabel.setText(message);
    }

    private void clearData() {
        entries.clear();
        lecturerNameLabel.setText("My Timetable");
        summaryLabel.setText("Timetable unavailable.");
        placeholder("Timetable is unavailable. See the message below for the next step.");
    }

    private void placeholder(String message) {
        Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(420);
        timetableTable.setPlaceholder(label);
    }

    @FXML
    private void handleBack() {
        try {
            requireLecturer();
            NavigationUtil.navigate(lecturerTimetableRoot, "/fxml/lecturer_dashboard.fxml", "FTMS - Lecturer Dashboard");
        } catch (IOException | RuntimeException error) {
            showError(TimetableTaskRunner.errorMessage("Could not open the Lecturer Dashboard.", error));
        }
    }

    private void showError(String message) {
        if (!SessionManager.isCurrentSession(loginIdentity)) clearData();
        statusLabel.setText(TimetableTaskRunner.statusMessage(message));
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Lecturer Timetable");
        alert.setHeaderText(null);
        if (lecturerTimetableRoot.getScene() != null && lecturerTimetableRoot.getScene().getWindow() != null) {
            alert.initOwner(lecturerTimetableRoot.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value == null || value.isBlank() ? "-" : value);
    }

    private static String time(LocalTime value) {
        return value == null ? "-" : TIME_FORMAT.format(value);
    }

    private static String dates(TimetableEntry entry) {
        return (entry.effectiveFrom() == null ? "Any start" : entry.effectiveFrom().toString())
                + " to " + (entry.effectiveTo() == null ? "No end" : entry.effectiveTo().toString());
    }
}
