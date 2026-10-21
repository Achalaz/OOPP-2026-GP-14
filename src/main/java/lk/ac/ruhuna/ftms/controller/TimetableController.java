
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
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.TimetableService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
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
        backButton.disableProperty().bind(busy);

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

                        offeringComboBox.getSelectionModel()
                                .clearSelection();

                    } finally {

                        updatingFilter = false;
                    }

                    entries.setAll(result.timetable());

                    updateSummary(
                            result.timetable(),
                            null
                    );

                    statusLabel.setText(
                            result.timetable().size()
                                    + " timetable entries loaded."
                    );
                }
        );
    }

    // Refresh timetable

    @FXML
    private void handleRefresh() {

        loadTimetable(
                offeringComboBox.getValue()
        );
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

                    statusLabel.setText(
                            result.size()
                                    + " timetable entries loaded."
                    );
                }
        );
    }

    // Update summary

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

    private <T> void runTask(
            String message,
            Callable<T> work,
            Consumer<T> onSuccess) {

        if (busy.get()) {
            return;
        }

        try {

            requireTechnicalOfficer();

        } catch (SecurityException error) {

            clearData();

            showError(
                    "Your session has ended. Please sign in again.",
                    error
            );

            return;
        }

        busy.set(true);
        statusLabel.setText(message);

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(event -> {

            try {

                requireTechnicalOfficer();

                onSuccess.accept(task.getValue());

            } catch (SecurityException error) {

                clearData();

                showError(
                        "Your session has ended. Please sign in again.",
                        error
                );

            } catch (RuntimeException error) {

                showError(
                        "Unable to display timetable data.",
                        error
                );

            } finally {

                busy.set(false);
            }
        });

        task.setOnFailed(event -> {

            busy.set(false);

            Throwable error = task.getException();

            if (error instanceof SecurityException) {

                clearData();

                showError(
                        "Timetable access was denied.",
                        error
                );

            } else {

                showError(
                        "Unable to load timetable. "
                                + "Check the database connection.",
                        error
                );
            }
        });

        Thread worker = new Thread(
                task,
                "ftms-timetable-loader"
        );

        worker.setDaemon(true);
        worker.start();
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

        statusLabel.setText(message);

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
