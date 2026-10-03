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
import lk.ac.ruhuna.ftms.exception.ResultCalculationException;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.ResultRow;
import lk.ac.ruhuna.ftms.service.ResultService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** JavaFX interaction only. Result rules and persistence are accessed through ResultService. */
public class ResultsController {
    @FXML private ComboBox<EligibilityOffering> offeringCombo;
    @FXML private ComboBox<ResultRow> enrollmentCombo;
    @FXML private Label caMarkLabel;
    @FXML private Label finalExamMarkLabel;
    @FXML private Label overallMarkLabel;
    @FXML private Label resultStatusLabel;
    @FXML private Label gradeLetterLabel;
    @FXML private Label gradePointLabel;
    @FXML private Label resultStateLabel;
    @FXML private Label reasonLabel;
    @FXML private Label statusLabel;
    @FXML private Button calculateButton;
    @FXML private Button saveButton;
    @FXML private Button clearButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private TableView<ResultRow> batchTable;
    @FXML private TableColumn<ResultRow, String> enrollmentColumn;
    @FXML private TableColumn<ResultRow, String> studentColumn;
    @FXML private TableColumn<ResultRow, String> caMarkColumn;
    @FXML private TableColumn<ResultRow, String> finalExamMarkColumn;
    @FXML private TableColumn<ResultRow, String> overallMarkColumn;
    @FXML private TableColumn<ResultRow, String> resultStatusColumn;
    @FXML private TableColumn<ResultRow, String> gradeLetterColumn;
    @FXML private TableColumn<ResultRow, String> gradePointColumn;
    @FXML private TableColumn<ResultRow, String> stateColumn;

    private final ResultService resultService;
    private final ObservableList<ResultRow> rows = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final ObjectProperty<Integer> readyEnrollment = new SimpleObjectProperty<>();
    private boolean updatingSelection;
    // Authentication integration may supply this later; the schema allows SQL NULL.
    private Integer calculatedBy;

    public ResultsController() { this(new ResultService()); }

    public ResultsController(ResultService resultService) {
        this.resultService = Objects.requireNonNull(resultService);
    }

    public void setCalculatedBy(Integer userId) {
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("Calculated-by user ID must be positive.");
        }
        calculatedBy = userId;
        readyEnrollment.set(null);
    }

    @FXML
    public void initialize() {
        enrollmentCombo.setItems(rows);
        batchTable.setItems(rows);
        batchTable.setPlaceholder(new Label("Select a course/offering to view enrollments."));
        column(enrollmentColumn, row -> Integer.toString(row.enrollmentId()));
        column(studentColumn, row -> row.registrationNo() + " - " + row.studentName());
        column(caMarkColumn, row -> number(row.result(), CourseResult::getCaMark));
        column(finalExamMarkColumn, row -> number(row.result(), CourseResult::getFinalExamMark));
        column(overallMarkColumn, row -> number(row.result(), CourseResult::getOverallMark));
        column(resultStatusColumn, row -> row.result() == null ? "PENDING" : row.result().getResultStatus().name());
        column(gradeLetterColumn, row -> grade(row.result()));
        column(gradePointColumn, row -> number(row.result(), CourseResult::getGradePoint));
        column(stateColumn, row -> state(row.result()));

        offeringCombo.disableProperty().bind(busy);
        enrollmentCombo.disableProperty().bind(busy.or(offeringCombo.valueProperty().isNull()));
        batchTable.disableProperty().bind(busy);
        calculateButton.disableProperty().bind(busy.or(enrollmentCombo.valueProperty().isNull()));
        saveButton.disableProperty().bind(busy.or(readyEnrollment.isNull()));
        clearButton.disableProperty().bind(busy);
        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);

        offeringCombo.valueProperty().addListener((observable, previous, selected) -> loadEnrollments(selected));
        enrollmentCombo.valueProperty().addListener((observable, previous, selected) -> selectRow(selected));
        batchTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> selectRow(selected));
        display(null);
        loadOfferings();
    }

    private void column(TableColumn<ResultRow, String> column, Function<ResultRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private void loadOfferings() {
        runTask("Loading course offerings...", resultService::getOfferings, offerings -> {
            offeringCombo.setItems(FXCollections.observableArrayList(offerings));
            statusLabel.setText(offerings.isEmpty() ? "No course offerings are available."
                    : "Select a course/offering and a student/enrollment.");
        });
    }

    private void loadEnrollments(EligibilityOffering offering) {
        readyEnrollment.set(null);
        rows.clear();
        selectRow(null);
        if (offering == null) {
            batchTable.setPlaceholder(new Label("Select a course/offering to view enrollments."));
            statusLabel.setText("Select a course/offering and a student/enrollment.");
            return;
        }
        batchTable.setPlaceholder(new Label("No enrollments found for this offering."));
        runTask("Loading batch results...", () -> resultService.getBatchResults(offering.offeringId()), results -> {
            rows.setAll(results);
            statusLabel.setText(results.isEmpty() ? "No enrollments found for this offering."
                    : results.size() + " enrollments loaded. Select one to view or calculate a result.");
        });
    }

    private void selectRow(ResultRow row) {
        if (updatingSelection) { return; }
        updatingSelection = true;
        try {
            readyEnrollment.set(null);
            if (row == null) {
                enrollmentCombo.getSelectionModel().clearSelection();
                batchTable.getSelectionModel().clearSelection();
            } else {
                enrollmentCombo.getSelectionModel().select(row);
                batchTable.getSelectionModel().select(row);
            }
            display(row);
        } finally { updatingSelection = false; }
    }

    @FXML
    private void handleCalculate() {
        ResultRow selected = enrollmentCombo.getValue();
        if (selected == null) { return; }
        readyEnrollment.set(null);
        runTask("Calculating result...", () -> resultService.calculateResult(selected.enrollmentId()), result -> {
            replaceRow(selected.withResult(result));
            readyEnrollment.set(selected.enrollmentId());
            statusLabel.setText("Result calculated. Save / Update recalculates and saves an unpublished draft.");
        });
    }

    @FXML
    private void handleSave() {
        ResultRow selected = enrollmentCombo.getValue();
        if (selected == null || !Objects.equals(readyEnrollment.get(), selected.enrollmentId())) { return; }
        readyEnrollment.set(null);
        Integer userId = calculatedBy;
        runTask("Recalculating and saving result...",
                () -> resultService.calculateAndSaveResult(selected.enrollmentId(), userId), result -> {
                    replaceRow(selected.withResult(result));
                    statusLabel.setText("Unpublished result saved for enrollment " + selected.enrollmentId() + ".");
                });
    }

    private void replaceRow(ResultRow replacement) {
        updatingSelection = true;
        try {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).enrollmentId() == replacement.enrollmentId()) {
                    rows.set(index, replacement);
                    break;
                }
            }
            enrollmentCombo.getSelectionModel().select(replacement);
            batchTable.getSelectionModel().select(replacement);
            display(replacement);
        } finally { updatingSelection = false; }
    }

    @FXML
    private void handleClear() {
        offeringCombo.getSelectionModel().clearSelection();
        rows.clear();
        selectRow(null);
        statusLabel.setText("Select a course/offering and a student/enrollment.");
    }

    @FXML
    private void handleRefresh() {
        if (offeringCombo.getValue() == null) { loadOfferings(); }
        else { loadEnrollments(offeringCombo.getValue()); }
    }

    @FXML
    private void handleBack() {
        try { NavigationUtil.navigate(batchTable, "/fxml/marks_entry.fxml", "FTMS - Marks Entry"); }
        catch (IOException error) { showError("Unable to open Marks Entry. Please retry.", error); }
    }

    private void display(ResultRow row) {
        CourseResult result = row == null ? null : row.result();
        caMarkLabel.setText(number(result, CourseResult::getCaMark));
        finalExamMarkLabel.setText(number(result, CourseResult::getFinalExamMark));
        overallMarkLabel.setText(number(result, CourseResult::getOverallMark));
        resultStatusLabel.setText(row == null ? "--" : result == null ? "PENDING" : result.getResultStatus().name());
        gradeLetterLabel.setText(grade(result));
        gradePointLabel.setText(number(result, CourseResult::getGradePoint));
        resultStateLabel.setText(row == null ? "No enrollment selected" : row + " | " + state(result));
        reasonLabel.setText(row == null ? "Select an enrollment to view a result." : resultService.describe(result));
    }

    private static String number(CourseResult result, Function<CourseResult, BigDecimal> property) {
        BigDecimal value = result == null ? null : property.apply(result);
        return value == null ? "--" : value.toPlainString();
    }

    private static String grade(CourseResult result) {
        return result == null || result.getGradeLetter() == null ? "--" : result.getGradeLetter();
    }

    private static String state(CourseResult result) {
        if (result == null) { return "Not calculated"; }
        if (result.getResultId() == 0) { return "Calculated - not saved"; }
        return result.isPublished() ? "Published" : "Saved draft";
    }

    private <T> void runTask(String message, Callable<T> work, Consumer<T> onSuccess) {
        busy.set(true);
        statusLabel.setText(message);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            readyEnrollment.set(null);
            Throwable error = task.getException();
            showError(error instanceof ResultCalculationException ? error.getMessage()
                    : "Unable to complete the result action. Check the database connection and retry.", error);
        });
        Thread thread = new Thread(task, "ftms-results");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message, Throwable error) {
        statusLabel.setText(message);
        error.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(error instanceof ResultCalculationException ? "Result Validation" : "Results Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
