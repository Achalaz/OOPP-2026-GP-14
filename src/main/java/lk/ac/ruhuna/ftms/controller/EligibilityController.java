
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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

import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.EligibilityResult;
import lk.ac.ruhuna.ftms.model.EligibilityRow;
import lk.ac.ruhuna.ftms.service.AttendanceEligibilityProvider;
import lk.ac.ruhuna.ftms.service.AttendanceEligibilityService;
import lk.ac.ruhuna.ftms.service.EligibilityService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class EligibilityController {

    @FXML private ComboBox<EligibilityOffering> offeringCombo;
    @FXML private ComboBox<EligibilityRow> enrollmentCombo;

    @FXML private Label caPercentageLabel;
    @FXML private Label caEligibilityLabel;
    @FXML private Label attendancePercentageLabel;
    @FXML private Label attendanceEligibilityLabel;
    @FXML private Label overallEligibilityLabel;
    @FXML private Label reasonLabel;
    @FXML private Label resultStateLabel;
    @FXML private Label statusLabel;

    @FXML private Button calculateButton;
    @FXML private Button saveButton;
    @FXML private Button clearButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;

    @FXML private TableView<EligibilityRow> batchTable;

    @FXML private TableColumn<EligibilityRow, String>
            enrollmentColumn;

    @FXML private TableColumn<EligibilityRow, String>
            caPercentageColumn;

    @FXML private TableColumn<EligibilityRow, String>
            caEligibilityColumn;

    @FXML private TableColumn<EligibilityRow, String>
            attendancePercentageColumn;

    @FXML private TableColumn<EligibilityRow, String>
            attendanceEligibilityColumn;

    @FXML private TableColumn<EligibilityRow, String>
            overallEligibilityColumn;

    @FXML private TableColumn<EligibilityRow, String>
            reasonColumn;

    @FXML private TableColumn<EligibilityRow, String>
            stateColumn;

    private final EligibilityDAO eligibilityDAO;

    private final EligibilityService eligibilityService;

    private final ObservableList<EligibilityRow> rows =
            FXCollections.observableArrayList();

    private final BooleanProperty busy =
            new SimpleBooleanProperty(false);

    private final ObjectProperty<Integer> readyEnrollment =
            new SimpleObjectProperty<>();

    private boolean updatingSelection;

    // NEW: Attendance Eligibility Integration
    // A final decision needs the officially confirmed cutoff.
    private AttendanceEligibilityProvider attendanceProvider =
            new AttendanceEligibilityService();

    // Authentication integration pending
    private Integer calculatedBy;

    // Default constructor
    public EligibilityController() {
        this(
                new EligibilityDAO(),
                new EligibilityService()
        );
    }

    // Constructor with dependency injection
    public EligibilityController(
            EligibilityDAO eligibilityDAO,
            EligibilityService eligibilityService) {

        this.eligibilityDAO =
                Objects.requireNonNull(eligibilityDAO);

        this.eligibilityService =
                Objects.requireNonNull(eligibilityService);
    }

    // Allow another attendance provider to be supplied
    public void setAttendanceProvider(
            AttendanceEligibilityProvider provider) {

        attendanceProvider = Objects.requireNonNull(provider);
        readyEnrollment.set(null);
    }

    // Set user who calculates eligibility
    public void setCalculatedBy(Integer calculatedBy) {

        if (calculatedBy != null && calculatedBy <= 0) {

            throw new IllegalArgumentException(
                    "Calculated-by user ID must be positive."
            );
        }

        this.calculatedBy = calculatedBy;
    }

    @FXML
    public void initialize() {

        enrollmentCombo.setItems(rows);
        batchTable.setItems(rows);

        batchTable.setPlaceholder(
                new Label(
                        "Select a course/offering to view enrollments."
                )
        );

        column(
                enrollmentColumn,
                row -> Integer.toString(row.enrollmentId())
        );

        column(
                caPercentageColumn,
                row -> percentage(row.caPercentage())
        );

        column(
                caEligibilityColumn,
                row -> eligibility(row.caEligible())
        );

        column(
                attendancePercentageColumn,
                row -> percentage(row.attendancePercentage())
        );

        column(
                attendanceEligibilityColumn,
                row -> eligibility(row.attendanceEligible())
        );

        column(
                overallEligibilityColumn,
                row -> eligibility(row.overallEligible())
        );

        column(
                reasonColumn,
                row -> reason(row)
        );

        column(
                stateColumn,
                row -> state(row)
        );

        // Disable controls during database operations
        offeringCombo.disableProperty().bind(busy);

        enrollmentCombo.disableProperty().bind(
                busy.or(offeringCombo.valueProperty().isNull())
        );

        batchTable.disableProperty().bind(busy);

        calculateButton.disableProperty().bind(
                busy.or(enrollmentCombo.valueProperty().isNull())
        );

        saveButton.disableProperty().bind(
                busy.or(readyEnrollment.isNull())
        );

        clearButton.disableProperty().bind(busy);
        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);

        // Offering selection
        offeringCombo.valueProperty().addListener(
                (observable, previous, selected) ->
                        loadEnrollments(selected)
        );

        // Enrollment selection
        enrollmentCombo.valueProperty().addListener(
                (observable, previous, selected) ->
                        selectRow(selected)
        );

        // Table row selection
        batchTable.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, previous, selected) ->
                                selectRow(selected)
                );

        display(null);
        loadOfferings();
    }

    // Configure table columns
    private void column(
            TableColumn<EligibilityRow, String> column,
            Function<EligibilityRow, String> value) {

        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        value.apply(cell.getValue())
                )
        );
    }

    // Load course offerings
    private void loadOfferings() {

        runTask(
                "Loading course offerings...",
                eligibilityDAO::findOfferings,
                offerings -> {

                    offeringCombo.setItems(
                            FXCollections.observableArrayList(
                                    offerings
                            )
                    );

                    statusLabel.setText(
                            offerings.isEmpty()
                                    ? "No course offerings are available."
                                    : "Select a course/offering and an enrollment."
                    );
                }
        );
    }

    // Load students for selected offering
    private void loadEnrollments(
            EligibilityOffering offering) {

        readyEnrollment.set(null);
        rows.clear();
        selectRow(null);

        if (offering == null) {

            batchTable.setPlaceholder(
                    new Label(
                            "Select a course/offering to view enrollments."
                    )
            );

            statusLabel.setText(
                    "Select a course/offering and an enrollment."
            );

            return;
        }

        batchTable.setPlaceholder(
                new Label(
                        "No enrollments found for this offering."
                )
        );

        runTask(
                "Loading eligibility results...",

                () -> eligibilityDAO.findByOfferingId(
                        offering.offeringId()
                ),

                results -> {

                    rows.setAll(results);

                    statusLabel.setText(
                            results.isEmpty()
                                    ? "No enrollments found for this offering."
                                    : results.size()
                                    + " enrollments loaded. Select one to view individual eligibility."
                    );
                }
        );
    }

    // Select enrollment row
    private void selectRow(EligibilityRow row) {

        if (updatingSelection) {
            return;
        }

        updatingSelection = true;

        try {

            readyEnrollment.set(null);

            if (row == null) {

                enrollmentCombo.getSelectionModel()
                        .clearSelection();

                batchTable.getSelectionModel()
                        .clearSelection();

            } else {

                enrollmentCombo.getSelectionModel()
                        .select(row);

                batchTable.getSelectionModel()
                        .select(row);
            }

            display(row);

        } finally {
            updatingSelection = false;
        }
    }

    // Calculate CA + Attendance Eligibility
    @FXML
    private void handleCalculate() {

        EligibilityRow selected =
                enrollmentCombo.getValue();

        if (selected == null) {
            return;
        }

        readyEnrollment.set(null);

        int enrollmentId = selected.enrollmentId();

        AttendanceEligibilityProvider provider =
                attendanceProvider;

        runTask(
                "Calculating eligibility...",

                () -> {

                    // NEW: Get attendance decision from
                    // AttendanceEligibilityService
                    var attendance =
                            provider.findByEnrollmentId(
                                    enrollmentId
                            );

                    if (attendance.isPresent()) {

                        var decision = attendance.get();

                        EligibilityResult result =
                                eligibilityService.evaluateEligibility(
                                        enrollmentId,
                                        decision.percentage(),
                                        decision.eligible()
                                );

                        return EligibilityRow.fromResult(
                                result,
                                false
                        );
                    }

                    // If attendance decision is unavailable,
                    // calculate CA without inventing eligibility.
                    return new EligibilityRow(
                            enrollmentId,

                            eligibilityService.calculateCAPercentage(
                                    enrollmentId
                            ),

                            eligibilityService.isCAEligible(
                                    enrollmentId
                            ),

                            null,
                            null,
                            null,

                            "Awaiting attendance percentage and eligibility decision.",

                            false
                    );
                },

                row -> {

                    replaceRow(row);

                    if (row.attendanceEligible() != null) {

                        readyEnrollment.set(enrollmentId);

                        statusLabel.setText(
                                "Eligibility calculated. Save / Update to store this result."
                        );

                    } else {

                        statusLabel.setText(
                                "CA calculated. Saving requires an attendance decision."
                        );
                    }
                }
        );
    }

    // Save calculated eligibility
    @FXML
    private void handleSave() {

        EligibilityRow selected =
                enrollmentCombo.getValue();

        if (selected == null
                || !Objects.equals(
                readyEnrollment.get(),
                selected.enrollmentId()
        )) {

            return;
        }

        readyEnrollment.set(null);

        int enrollmentId = selected.enrollmentId();

        Integer userId = calculatedBy;

        AttendanceEligibilityProvider provider =
                attendanceProvider;

        runTask(
                "Saving eligibility...",

                () -> {

                    var attendance =
                            provider.findByEnrollmentId(
                                    enrollmentId
                            ).orElseThrow(
                                    () -> new IllegalStateException(
                                            "Attendance decision is no longer available. Calculate again."
                                    )
                            );

                    // Recalculate before saving
                    return eligibilityService
                            .evaluateAndSaveEligibility(
                                    enrollmentId,
                                    attendance.percentage(),
                                    attendance.eligible(),
                                    userId
                            );
                },

                result -> {

                    replaceRow(
                            EligibilityRow.fromResult(
                                    result,
                                    true
                            )
                    );

                    statusLabel.setText(
                            "Eligibility saved successfully for enrollment "
                                    + enrollmentId
                                    + "."
                    );
                }
        );
    }

    // Replace calculated table row
    private void replaceRow(
            EligibilityRow replacement) {

        updatingSelection = true;

        try {

            for (int index = 0;
                 index < rows.size();
                 index++) {

                if (rows.get(index).enrollmentId()
                        == replacement.enrollmentId()) {

                    rows.set(index, replacement);
                    break;
                }
            }

            enrollmentCombo.getSelectionModel()
                    .select(replacement);

            batchTable.getSelectionModel()
                    .select(replacement);

            display(replacement);

        } finally {
            updatingSelection = false;
        }
    }

    // Clear selected offering and rows
    @FXML
    private void handleClear() {

        offeringCombo.getSelectionModel()
                .clearSelection();

        rows.clear();
        selectRow(null);

        statusLabel.setText(
                "Select a course/offering and an enrollment."
        );
    }

    // Refresh current eligibility data
    @FXML
    private void handleRefresh() {

        if (offeringCombo.getValue() == null) {

            loadOfferings();

        } else {

            loadEnrollments(
                    offeringCombo.getValue()
            );
        }
    }

    // Navigate back to Marks Entry
    @FXML
    private void handleBack() {

        try {

            NavigationUtil.navigate(
                    batchTable,
                    "/fxml/marks_entry.fxml",
                    "FTMS - Marks Entry"
            );

        } catch (IOException e) {

            showError(
                    "Unable to open the Marks Dashboard.",
                    e
            );
        }
    }

    // Display individual eligibility details
    private void display(EligibilityRow row) {

        caPercentageLabel.setText(
                row == null
                        ? "--"
                        : percentage(row.caPercentage())
        );

        caEligibilityLabel.setText(
                row == null
                        ? "--"
                        : eligibility(row.caEligible())
        );

        attendancePercentageLabel.setText(
                row == null
                        ? "--"
                        : percentage(row.attendancePercentage())
        );

        attendanceEligibilityLabel.setText(
                row == null
                        ? "--"
                        : eligibility(row.attendanceEligible())
        );

        overallEligibilityLabel.setText(
                row == null
                        ? "--"
                        : eligibility(row.overallEligible())
        );

        reasonLabel.setText(
                row == null
                        ? "Select an enrollment to view eligibility."
                        : reason(row)
        );

        resultStateLabel.setText(
                row == null
                        ? "No enrollment selected"
                        : row + " | " + state(row)
        );
    }

    // Format percentage
    private static String percentage(Double value) {

        return value == null
                ? "Pending"
                : String.format(
                Locale.ROOT,
                "%.2f%%",
                value
        );
    }

    // Format eligibility status
    private static String eligibility(Boolean value) {

        if (value == null) {
            return "Pending";
        }

        return value
                ? "Eligible"
                : "Not eligible";
    }

    // Get reason text
    private static String reason(EligibilityRow row) {

        return row.reason() == null
                || row.reason().isBlank()
                ? "Not yet calculated."
                : row.reason();
    }

    // Get calculation state
    private static String state(EligibilityRow row) {

        return row.saved()
                ? "Saved result"
                : row.caPercentage() == null
                ? "Not calculated"
                : "Calculated - not saved";
    }

    // Run database tasks in background
    private <T> void runTask(
            String message,
            Callable<T> work,
            Consumer<T> onSuccess) {

        busy.set(true);
        statusLabel.setText(message);

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(event -> {

            busy.set(false);
            onSuccess.accept(task.getValue());
        });

        task.setOnFailed(event -> {

            busy.set(false);
            readyEnrollment.set(null);

            showError(
                    "Unable to complete the eligibility action. Please retry.",
                    task.getException()
            );
        });

        Thread thread = new Thread(
                task,
                "ftms-eligibility"
        );

        thread.setDaemon(true);
        thread.start();
    }

    // Show JavaFX error dialog
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

        alert.setTitle("Eligibility Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
