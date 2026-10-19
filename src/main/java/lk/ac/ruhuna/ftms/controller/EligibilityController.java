
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
import javafx.scene.control.*;

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

    // Attendance integration
    // Official cutoff is not configured yet.
    private AttendanceEligibilityProvider attendanceProvider =
            new AttendanceEligibilityService();

    // Authentication integration pending
    private Integer calculatedBy;

    public EligibilityController() {
        this(
                new EligibilityDAO(),
                new EligibilityService()
        );
    }

    public EligibilityController(
            EligibilityDAO eligibilityDAO,
            EligibilityService eligibilityService) {

        this.eligibilityDAO =
                Objects.requireNonNull(eligibilityDAO);

        this.eligibilityService =
                Objects.requireNonNull(eligibilityService);
    }

    public void setAttendanceProvider(
            AttendanceEligibilityProvider provider) {

        attendanceProvider = Objects.requireNonNull(provider);
        readyEnrollment.set(null);
    }

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

        column(reasonColumn, EligibilityController::reason);
        column(stateColumn, EligibilityController::state);

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

        offeringCombo.valueProperty().addListener(
                (obs, oldValue, selected) ->
                        loadEnrollments(selected)
        );

        enrollmentCombo.valueProperty().addListener(
                (obs, oldValue, selected) ->
                        selectRow(selected)
        );

        batchTable.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, selected) ->
                                selectRow(selected)
                );

        display(null);
        loadOfferings();
    }

    private void column(
            TableColumn<EligibilityRow, String> column,
            Function<EligibilityRow, String> value) {

        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        value.apply(cell.getValue())
                )
        );
    }

    private void loadOfferings() {

        runTask(
                "Loading course offerings...",
                eligibilityDAO::findOfferings,

                offerings -> {

                    offeringCombo.setItems(
                            FXCollections.observableArrayList(offerings)
                    );

                    statusLabel.setText(
                            offerings.isEmpty()
                                    ? "No course offerings are available."
                                    : "Select a course/offering and an enrollment."
                    );
                }
        );
    }

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
                                    + " enrollments loaded."
                    );
                }
        );
    }

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

                enrollmentCombo.getSelectionModel().select(row);

                batchTable.getSelectionModel().select(row);
            }

            display(row);

        } finally {
            updatingSelection = false;
        }
    }

    // Calculate CA and attendance percentage
    @FXML
    private void handleCalculate() {

        EligibilityRow selected = enrollmentCombo.getValue();

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

                    var attendance =
                            provider.findByEnrollmentId(enrollmentId);

                    // Official decision is available.
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

                    // No final decision yet.
                    // Still calculate raw attendance percentage.
                    Double attendancePercentage = null;

                    String reasonText =
                            "Attendance data or decision is not available yet.";

                    if (provider instanceof AttendanceEligibilityService service) {

                        var counts =
                                service.getAttendanceCounts(enrollmentId);

                        if (counts == null) {

                            reasonText =
                                    "No enrollment found for attendance calculation.";

                        } else if (counts.getTotalSessions() == 0) {

                            reasonText =
                                    "No Theory/Practical sessions available yet.";

                        } else {

                            attendancePercentage =
                                    counts.getCombinedPercentage();

                            if (counts.requiresPolicyDecision()) {

                                reasonText =
                                        "Attendance calculated. Special or unmarked statuses need an official policy.";

                            } else {

                                reasonText =
                                        "Attendance calculated. Waiting for the confirmed eligibility cutoff.";
                            }
                        }
                    }

                    return new EligibilityRow(

                            enrollmentId,

                            eligibilityService.calculateCAPercentage(
                                    enrollmentId
                            ),

                            eligibilityService.isCAEligible(
                                    enrollmentId
                            ),

                            attendancePercentage,

                            null, // Attendance eligibility pending
                            null, // Overall eligibility pending

                            reasonText,

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
                                row.attendancePercentage() == null
                                        ? "CA calculated. Attendance information is not yet available."
                                        : "CA and attendance percentages calculated. Eligibility decision is pending."
                        );
                    }
                }
        );
    }

    // Save only when a final attendance decision exists
    @FXML
    private void handleSave() {

        EligibilityRow selected = enrollmentCombo.getValue();

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
                            provider.findByEnrollmentId(enrollmentId)
                                    .orElseThrow(
                                            () -> new IllegalStateException(
                                                    "Attendance decision is no longer available."
                                            )
                                    );

                    return eligibilityService.evaluateAndSaveEligibility(
                            enrollmentId,
                            attendance.percentage(),
                            attendance.eligible(),
                            userId
                    );
                },

                result -> {

                    replaceRow(
                            EligibilityRow.fromResult(result, true)
                    );

                    statusLabel.setText(
                            "Eligibility saved successfully for enrollment "
                                    + enrollmentId
                    );
                }
        );
    }

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

    @FXML
    private void handleClear() {

        offeringCombo.getSelectionModel().clearSelection();

        rows.clear();
        selectRow(null);

        statusLabel.setText(
                "Select a course/offering and an enrollment."
        );
    }

    @FXML
    private void handleRefresh() {

        if (offeringCombo.getValue() == null) {
            loadOfferings();
        } else {
            loadEnrollments(offeringCombo.getValue());
        }
    }

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
                    "Unable to open Marks Entry.",
                    e
            );
        }
    }

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

    private static String percentage(Double value) {

        return value == null
                ? "Pending"
                : String.format(
                Locale.ROOT,
                "%.2f%%",
                value
        );
    }

    private static String eligibility(Boolean value) {

        if (value == null) {
            return "Pending";
        }

        return value ? "Eligible" : "Not eligible";
    }

    private static String reason(EligibilityRow row) {

        return row.reason() == null
                || row.reason().isBlank()
                ? "Not yet calculated."
                : row.reason();
    }

    private static String state(EligibilityRow row) {

        return row.saved()
                ? "Saved result"
                : row.caPercentage() == null
                ? "Not calculated"
                : "Calculated - not saved";
    }

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
                    "Unable to complete the eligibility action.",
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

    private void showError(
            String message,
            Throwable error) {

        statusLabel.setText(message);

        if (error != null) {
            error.printStackTrace();
        }

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Eligibility Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }
}
