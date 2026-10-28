
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
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.TimetableService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class TimetableController {

    @FXML private BorderPane timetableRoot;

    @FXML private Label officerNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;

    @FXML private ComboBox<AttendanceOffering> offeringComboBox;

    @FXML private Button refreshButton;
    @FXML private Button allCoursesButton;
    @FXML private Button backButton;

    @FXML private TableView<TimetableEntry> timetableTable;

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
            DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private SessionIdentity loginIdentity;
    private TimetableService timetableService;
    private TimetableTaskRunner tasks;

    private boolean updatingFilter;

    private record InitialData(
            List<AttendanceOffering> offerings,
            List<TimetableEntry> timetable
    ) {
    }

    // Initialize Technical Officer timetable page

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        User officer = requireTechnicalOfficer();

        timetableService = new TimetableService();
        tasks = new TimetableTaskRunner(timetableRoot, busy, statusLabel, loginIdentity, this::clearData);

        officerNameLabel.setText(
                "Department Timetable - " + officer.getFullName()
        );

        // Table columns

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
                        safeText(cell.getValue().room())
                )
        );

        lecturerColumn.setCellValueFactory(
                cell -> text(
                        safeText(cell.getValue().lecturerName())
                )
        );

        effectiveColumn.setCellValueFactory(
                cell -> text(
                        formatEffectiveDates(cell.getValue())
                )
        );

        timetableTable.setItems(entries);

        timetableTable.setPlaceholder(
                new Label("No timetable entries found.")
        );

        // Course selection

        offeringComboBox.setPromptText(
                "All Department Courses"
        );

        offeringComboBox.valueProperty().addListener(
                (observable, oldOffering, newOffering) -> {

                    if (!updatingFilter && !busy.get()) {
                        loadTimetable(newOffering);
                    }
                }
        );

        // Disable controls while loading

        refreshButton.disableProperty().bind(busy);
        allCoursesButton.disableProperty().bind(busy);

        offeringComboBox.disableProperty().bind(busy);
        timetableTable.disableProperty().bind(busy);

        summaryLabel.setText(
                "Loading department timetable..."
        );

        loadInitialData();
    }

    // Verify Technical Officer login

    private User requireTechnicalOfficer() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active Technical Officer session is required."
            );
        }

        return user;
    }

    // Load authorized courses and all timetable entries

    private void loadInitialData() {
        AttendanceOffering previousFilter = offeringComboBox.getValue();

        runTask(
                "Loading department timetable...",

                () -> new InitialData(
                        timetableService.getAuthorizedOfferings(),
                        timetableService.getDepartmentTimetable()
                ),

                result -> {

                    updatingFilter = true;

                    try {

                        offeringComboBox.getItems().setAll(
                                result.offerings()
                        );

                        offeringComboBox.setValue(previousFilter == null ? null :
                                result.offerings().stream().filter(o -> o.offeringId() == previousFilter.offeringId())
                                        .findFirst().orElse(null));

                    } finally {

                        updatingFilter = false;
                    }

                    AttendanceOffering filter = offeringComboBox.getValue();
                    entries.setAll(result.timetable().stream().filter(row -> filter == null
                            || row.offeringId() == filter.offeringId()).toList());

                    updateSummary(
                            List.copyOf(entries),
                            filter
                    );

                    showLoadedMessage(entries.size(), filter);
                }
        );
    }

    // Refresh timetable

    @FXML
    private void handleRefresh() {
        loadInitialData();
    }

    // Display all department courses

    @FXML
    private void handleAllCourses() {

        updatingFilter = true;

        try {

            offeringComboBox.getSelectionModel()
                    .clearSelection();

        } finally {

            updatingFilter = false;
        }

        loadTimetable(null);
    }

    // Load all entries or one selected course

    private void loadTimetable(
            AttendanceOffering offering) {

        runTask(
                "Loading timetable entries...",

                () -> {

                    if (offering == null) {

                        return timetableService
                                .getDepartmentTimetable();
                    }

                    return timetableService
                            .getOfferingTimetable(
                                    offering.offeringId()
                            );
                },

                result -> {

                    entries.setAll(result);

                    updateSummary(
                            result,
                            offering
                    );

                    showLoadedMessage(result.size(), offering);
                }
        );
    }

    // Update summary

    private void showLoadedMessage(int count, AttendanceOffering offering) {
        String scope = offering == null ? "your department"
                : offering.courseCode() + " (offering #" + offering.offeringId() + ")";
        String message;
        if (offeringComboBox.getItems().isEmpty()) {
            message = "No course offerings are available in your assigned department. Contact an administrator, then click Refresh.";
        } else if (count == 0) {
            message = "No timetable entries are available for " + scope + ". "
                    + (offering == null ? "Contact an administrator about the schedule, then click Refresh."
                    : "Choose All Courses to check the rest of your department's timetable.");
        } else {
            message = "Showing " + count + (count == 1 ? " timetable entry" : " timetable entries") + " for " + scope + ".";
        }
        Label placeholder = new Label(message);
        placeholder.setWrapText(true);
        placeholder.setMaxWidth(420);
        timetableTable.setPlaceholder(placeholder);
        statusLabel.setText(message);
    }

    private void updateSummary(
            List<TimetableEntry> result,
            AttendanceOffering offering) {

        long totalCourses = result.stream()
                .map(TimetableEntry::offeringId)
                .distinct()
                .count();

        String scope = offering == null
                ? "All Department Courses"
                : offering.courseCode();

        summaryLabel.setText(
                scope
                        + " | Courses: "
                        + totalCourses
                        + " | Sessions: "
                        + result.size()
        );
    }

    // Back to ORIGINAL Technical Officer Dashboard

    @FXML
    private void handleBack() {

        try {

            requireTechnicalOfficer();

            NavigationUtil.navigate(
                    timetableRoot,
                    "/fxml/technical_officer_dashboard.fxml",
                    "FTMS - Technical Officer Dashboard"
            );

        } catch (IOException | RuntimeException error) {

            showError(
                    "Unable to open the Technical Officer Dashboard.",
                    error
            );
        }
    }

    // Run database work without freezing JavaFX UI

    private <T> void runTask(String message, Callable<T> work, Consumer<T> onSuccess) {
        tasks.run(message, () -> {
            requireTechnicalOfficer();
            return work.call();
        }, result -> {
            requireTechnicalOfficer();
            onSuccess.accept(result);
        }, error -> {
            clearData();
            showError("Unable to load timetable.", error);
        });
    }

    // Clear timetable data after session failure

    private void clearData() {

        entries.clear();

        updatingFilter = true;

        try {

            offeringComboBox.getItems().clear();

        } finally {

            updatingFilter = false;
        }

        summaryLabel.setText(
                "Timetable unavailable."
        );
        timetableTable.setPlaceholder(new Label("Timetable is unavailable. See the message below for the next step."));
    }

    // Formatting helpers

    private static ReadOnlyStringWrapper text(
            String value) {

        return new ReadOnlyStringWrapper(
                value == null ? "" : value
        );
    }

    private static String safeText(
            String value) {

        return value == null || value.isBlank()
                ? "-"
                : value;
    }

    private static String formatTime(
            TimetableEntry entry) {

        LocalTime start = entry.startTime();
        LocalTime end = entry.endTime();

        String from = start == null
                ? "-"
                : start.format(TIME_FORMAT);

        String to = end == null
                ? "-"
                : end.format(TIME_FORMAT);

        return from + " - " + to;
    }

    private static String formatEffectiveDates(
            TimetableEntry entry) {

        LocalDate from = entry.effectiveFrom();
        LocalDate to = entry.effectiveTo();

        String start = from == null
                ? "-"
                : from.format(DATE_FORMAT);

        String end = to == null
                ? "-"
                : to.format(DATE_FORMAT);

        return start + " to " + end;
    }

    // Show errors

    private void showError(
            String message,
            Throwable error) {

        message = TimetableTaskRunner.errorMessage(message, error);
        statusLabel.setText(TimetableTaskRunner.statusMessage(message));

        if (error != null) {
            error.printStackTrace();
        }

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        alert.setTitle("Department Timetable");
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (timetableRoot.getScene() != null
                && timetableRoot.getScene()
                .getWindow() != null) {

            alert.initOwner(
                    timetableRoot.getScene().getWindow()
            );
        }

        alert.showAndWait();
    }
}
