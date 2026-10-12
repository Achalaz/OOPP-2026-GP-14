package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
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

import lk.ac.ruhuna.ftms.dao.ReportDAO.OfferingOption;
import lk.ac.ruhuna.ftms.dao.ReportDAO.StudentOption;
import lk.ac.ruhuna.ftms.model.ResultReportRow;
import lk.ac.ruhuna.ftms.service.ReportService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ReportsController {

    @FXML
    private ComboBox<StudentOption> studentCombo;

    @FXML
    private ComboBox<OfferingOption> offeringCombo;

    @FXML
    private TableView<ResultReportRow> reportTable;

    @FXML
    private TableColumn<ResultReportRow, String> registrationColumn;

    @FXML
    private TableColumn<ResultReportRow, String> studentNameColumn;

    @FXML
    private TableColumn<ResultReportRow, String> courseCodeColumn;

    @FXML
    private TableColumn<ResultReportRow, String> courseNameColumn;

    @FXML
    private TableColumn<ResultReportRow, String> creditsColumn;

    @FXML
    private TableColumn<ResultReportRow, String> caMarkColumn;

    @FXML
    private TableColumn<ResultReportRow, String> finalMarkColumn;

    @FXML
    private TableColumn<ResultReportRow, String> overallMarkColumn;

    @FXML
    private TableColumn<ResultReportRow, String> gradeColumn;

    @FXML
    private TableColumn<ResultReportRow, String> gradePointColumn;

    @FXML
    private TableColumn<ResultReportRow, String> statusColumn;

    @FXML
    private Label totalLabel;

    @FXML
    private Label passLabel;

    @FXML
    private Label failLabel;

    @FXML
    private Label pendingLabel;

    @FXML
    private Label averageLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button studentReportButton;

    @FXML
    private Button offeringReportButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button clearButton;

    @FXML
    private Button backButton;

    private final ReportService reportService =
            new ReportService();

    private final ObservableList<ResultReportRow> reportRows =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {

        reportTable.setItems(reportRows);

        setupColumns();

        clearSummary();

        statusLabel.setText(
                "Select a student or course offering."
        );

        loadSelectionData();
    }

    private void setupColumns() {

        registrationColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getRegistrationNo()
                                )
                        )
        );

        studentNameColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getStudentName()
                                )
                        )
        );

        courseCodeColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getCourseCode()
                                )
                        )
        );

        courseNameColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getCourseName()
                                )
                        )
        );

        creditsColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .getCredits()
                                )
                        )
        );

        caMarkColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .getCaMark()
                                )
                        )
        );

        finalMarkColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .getFinalExamMark()
                                )
                        )
        );

        overallMarkColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .getOverallMark()
                                )
                        )
        );

        gradeColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getGradeLetter()
                                )
                        )
        );

        gradePointColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .getGradePoint()
                                )
                        )
        );

        statusColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .getResultStatus()
                                )
                        )
        );
    }

    private void loadSelectionData() {

        Task<Void> task =
                new Task<>() {

                    @Override
                    protected Void call()
                            throws Exception {

                        List<StudentOption> students =
                                reportService
                                        .getStudents();

                        List<OfferingOption> offerings =
                                reportService
                                        .getCourseOfferings();

                        javafx.application.Platform.runLater(
                                () -> {

                                    studentCombo.setItems(
                                            FXCollections
                                                    .observableArrayList(
                                                            students
                                                    )
                                    );

                                    offeringCombo.setItems(
                                            FXCollections
                                                    .observableArrayList(
                                                            offerings
                                                    )
                                    );
                                }
                        );

                        return null;
                    }
                };

        task.setOnFailed(
                event ->
                        showError(
                                "Unable to load report selections.",
                                task.getException()
                        )
        );

        Thread thread =
                new Thread(
                        task,
                        "ftms-report-load"
                );

        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleStudentReport() {

        StudentOption student =
                studentCombo.getValue();

        if (student == null) {

            showValidation(
                    "Please select a student."
            );

            return;
        }

        runReportTask(
                "Loading student report...",

                () ->
                        reportService
                                .getStudentReport(
                                        student.studentId()
                                )
        );
    }

    @FXML
    private void handleOfferingReport() {

        OfferingOption offering =
                offeringCombo.getValue();

        if (offering == null) {

            showValidation(
                    "Please select a course offering."
            );

            return;
        }

        runReportTask(
                "Loading course offering report...",

                () ->
                        reportService
                                .getOfferingReport(
                                        offering.offeringId()
                                )
        );
    }

    private void runReportTask(
            String message,
            ReportLoader loader
    ) {

        setButtonsDisabled(true);

        statusLabel.setText(
                message
        );

        Task<List<ResultReportRow>> task =
                new Task<>() {

                    @Override
                    protected List<ResultReportRow> call()
                            throws Exception {

                        return loader.load();
                    }
                };

        task.setOnSucceeded(
                event -> {

                    setButtonsDisabled(false);

                    List<ResultReportRow> rows =
                            task.getValue();

                    reportRows.setAll(
                            rows
                    );

                    updateSummary(
                            rows
                    );

                    statusLabel.setText(
                            rows.size()
                                    + " report row(s) loaded."
                    );
                }
        );

        task.setOnFailed(
                event -> {

                    setButtonsDisabled(false);

                    Throwable error =
                            task.getException();

                    if (error
                            instanceof IllegalArgumentException) {

                        showValidation(
                                error.getMessage()
                        );

                    } else {

                        showError(
                                "Unable to load report.",
                                error
                        );
                    }
                }
        );

        Thread thread =
                new Thread(
                        task,
                        "ftms-report"
                );

        thread.setDaemon(true);
        thread.start();
    }

    private void updateSummary(
            List<ResultReportRow> rows
    ) {

        totalLabel.setText(
                String.valueOf(
                        reportService
                                .countTotalResults(
                                        rows
                                )
                )
        );

        passLabel.setText(
                String.valueOf(
                        reportService
                                .countPassed(
                                        rows
                                )
                )
        );

        failLabel.setText(
                String.valueOf(
                        reportService
                                .countFailed(
                                        rows
                                )
                )
        );

        pendingLabel.setText(
                String.valueOf(
                        reportService
                                .countPending(
                                        rows
                                )
                )
        );

        Double average =
                reportService
                        .calculateAverageOverallMark(
                                rows
                        );

        averageLabel.setText(
                formatNumber(
                        average
                )
        );
    }

    @FXML
    private void handleRefresh() {

        reportRows.clear();

        clearSummary();

        studentCombo.getSelectionModel()
                .clearSelection();

        offeringCombo.getSelectionModel()
                .clearSelection();

        loadSelectionData();

        statusLabel.setText(
                "Report data refreshed."
        );
    }

    @FXML
    private void handleClear() {

        reportRows.clear();

        studentCombo.getSelectionModel()
                .clearSelection();

        offeringCombo.getSelectionModel()
                .clearSelection();

        clearSummary();

        statusLabel.setText(
                "Select a student or course offering."
        );
    }

    @FXML
    private void handleBack() {

        try {

            NavigationUtil.navigate(
                    reportTable,
                    "/fxml/gpa.fxml",
                    "FTMS - SGPA / CGPA Management"
            );

        } catch (IOException error) {

            showError(
                    "Unable to return to GPA Management.",
                    error
            );
        }
    }

    private void setButtonsDisabled(
            boolean disabled
    ) {

        studentReportButton.setDisable(
                disabled
        );

        offeringReportButton.setDisable(
                disabled
        );

        refreshButton.setDisable(
                disabled
        );

        clearButton.setDisable(
                disabled
        );

        backButton.setDisable(
                disabled
        );
    }

    private void clearSummary() {

        totalLabel.setText("0");
        passLabel.setText("0");
        failLabel.setText("0");
        pendingLabel.setText("0");
        averageLabel.setText("--");
    }

    private String valueOrDash(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return "--";
        }

        return value;
    }

    private String formatNumber(
            Double value
    ) {

        if (value == null) {
            return "--";
        }

        return String.format(
                Locale.ROOT,
                "%.2f",
                value
        );
    }

    private String formatNumber(
            double value
    ) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                value
        );
    }

    private void showValidation(
            String message
    ) {

        statusLabel.setText(
                message
        );

        Alert alert =
                new Alert(
                        Alert.AlertType.WARNING
                );

        alert.setTitle(
                "Report Validation"
        );

        alert.setHeaderText(
                null
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    private void showError(
            String message,
            Throwable error
    ) {

        statusLabel.setText(
                message
        );

        if (error != null) {
            error.printStackTrace();
        }

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "Report Error"
        );

        alert.setHeaderText(
                null
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    @FunctionalInterface
    private interface ReportLoader {

        List<ResultReportRow> load()
                throws Exception;
    }
}