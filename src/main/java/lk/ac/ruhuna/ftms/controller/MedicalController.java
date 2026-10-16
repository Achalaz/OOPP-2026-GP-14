
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

import lk.ac.ruhuna.ftms.model.Medical;
import lk.ac.ruhuna.ftms.service.MedicalService;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MedicalController {

    // Main layout

    @FXML
    private BorderPane medicalRoot;

    // Medical information fields

    @FXML
    private TextField medicalIdField;

    @FXML
    private TextField studentIdField;

    @FXML
    private TextField offeringIdField;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private TextArea reasonArea;

    @FXML
    private TextField documentPathField;

    // Medical review fields

    @FXML
    private TextField reviewerIdField;

    @FXML
    private TextArea reviewNoteArea;

    // Medical records table

    @FXML
    private TableView<Medical> medicalTable;

    @FXML
    private TableColumn<Medical, Integer> medicalIdColumn;

    @FXML
    private TableColumn<Medical, Integer> studentIdColumn;

    @FXML
    private TableColumn<Medical, String> periodColumn;

    @FXML
    private TableColumn<Medical, String> reasonColumn;

    @FXML
    private TableColumn<Medical, String> statusColumn;

    @FXML
    private Label statusLabel;

    // Service layer

    private final MedicalService medicalService =
            new MedicalService();

    private final ObservableList<Medical> medicalRecords =
            FXCollections.observableArrayList();

    // Initialize table

    @FXML
    public void initialize() {

        medicalIdColumn.setCellValueFactory(
                cell -> new SimpleIntegerProperty(
                        cell.getValue().getMedicalId()
                ).asObject()
        );

        studentIdColumn.setCellValueFactory(
                cell -> new SimpleIntegerProperty(
                        cell.getValue().getStudentId()
                ).asObject()
        );

        periodColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getFromDate()
                                + " to "
                                + cell.getValue().getToDate()
                )
        );

        reasonColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        safeText(cell.getValue().getReason())
                )
        );

        statusColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        safeText(cell.getValue().getStatus())
                )
        );

        medicalTable.setItems(medicalRecords);

        medicalTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {

                    if (selected != null) {
                        displayMedical(selected);
                    }
                });

        statusLabel.setText("Medical Management Ready");
    }

    // Submit a new medical request

    @FXML
    private void handleSubmitMedical() {

        try {

            Medical medical = buildMedicalFromForm();

            runTask(
                    () -> medicalService.submitMedical(medical),
                    saved -> {

                        updateTableRow(saved);

                        statusLabel.setText(
                                "Medical submitted successfully. ID: "
                                        + saved.getMedicalId()
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Update a pending medical

    @FXML
    private void handleUpdateMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            Medical medical = buildMedicalFromForm();
            medical.setMedicalId(medicalId);

            runTask(
                    () -> {

                        boolean updated =
                                medicalService.updatePendingMedical(
                                        medical
                                );

                        if (!updated) {
                            throw new IllegalStateException(
                                    "Medical could not be updated."
                            );
                        }

                        return medicalService.getMedicalById(
                                medicalId
                        );
                    },

                    updated -> {

                        updateTableRow(updated);

                        statusLabel.setText(
                                "Medical updated successfully."
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Search a medical by ID

    @FXML
    private void handleFindMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            runTask(
                    () -> medicalService.getMedicalById(
                            medicalId
                    ),

                    medical -> {

                        medicalRecords.clear();

                        if (medical == null) {

                            statusLabel.setText(
                                    "Medical record not found."
                            );

                            return;
                        }

                        medicalRecords.add(medical);

                        medicalTable.getSelectionModel()
                                .select(medical);

                        statusLabel.setText(
                                "Medical record found."
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Load medical records of one student

    @FXML
    private void handleLoadStudentMedicals() {

        try {

            int studentId = requiredId(
                    studentIdField,
                    "Student ID"
            );

            runTask(
                    () -> medicalService.getMedicalsByStudent(
                            studentId
                    ),

                    records -> {

                        medicalRecords.setAll(records);

                        statusLabel.setText(
                                "Loaded "
                                        + records.size()
                                        + " medical records."
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Load all records.
    // Staff authorization must be added before deployment.

    @FXML
    private void handleLoadAllMedicals() {

        runTask(
                medicalService::getAllMedicals,

                records -> {

                    medicalRecords.setAll(records);

                    statusLabel.setText(
                            "Loaded "
                                    + records.size()
                                    + " medical records."
                    );
                }
        );
    }

    // Approve a pending medical

    @FXML
    private void handleApproveMedical() {
        reviewMedical(true);
    }

    // Reject a pending medical

    @FXML
    private void handleRejectMedical() {
        reviewMedical(false);
    }

    // Common approve/reject operation

    private void reviewMedical(boolean approve) {

        try {

            int medicalId = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            int reviewerId = requiredId(
                    reviewerIdField,
                    "Reviewer ID"
            );

            String note = reviewNoteArea.getText();

            runTask(
                    () -> {

                        if (approve) {

                            medicalService.approveMedical(
                                    medicalId,
                                    reviewerId,
                                    note
                            );

                        } else {

                            medicalService.rejectMedical(
                                    medicalId,
                                    reviewerId,
                                    note
                            );
                        }

                        return medicalService.getMedicalById(
                                medicalId
                        );
                    },

                    reviewed -> {

                        updateTableRow(reviewed);

                        statusLabel.setText(
                                approve
                                        ? "Medical approved successfully."
                                        : "Medical rejected successfully."
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Delete a pending medical

    @FXML
    private void handleDeleteMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            int studentId = requiredId(
                    studentIdField,
                    "Student ID"
            );

            runTask(
                    () -> {

                        boolean deleted =
                                medicalService.deletePendingMedical(
                                        medicalId,
                                        studentId
                                );

                        if (!deleted) {
                            throw new IllegalStateException(
                                    "Medical could not be deleted."
                            );
                        }

                        return medicalId;
                    },

                    deletedId -> {

                        medicalRecords.removeIf(
                                medical ->
                                        medical.getMedicalId()
                                                == deletedId
                        );

                        clearForm();

                        statusLabel.setText(
                                "Medical deleted successfully."
                        );
                    }
            );

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Browse supporting document

    @FXML
    private void handleBrowseDocument() {

        FileChooser chooser = new FileChooser();

        chooser.setTitle("Select Medical Document");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Documents",
                        "*.pdf",
                        "*.jpg",
                        "*.jpeg",
                        "*.png"
                )
        );

        File selected = chooser.showOpenDialog(
                documentPathField.getScene().getWindow()
        );

        if (selected != null) {

            documentPathField.setText(
                    selected.getAbsolutePath()
            );
        }
    }

    // Clear input fields

    @FXML
    private void handleClear() {

        medicalTable.getSelectionModel().clearSelection();

        clearForm();

        statusLabel.setText("Form cleared.");
    }

    private void clearForm() {

        medicalIdField.clear();
        studentIdField.clear();
        offeringIdField.clear();

        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);

        reasonArea.clear();
        documentPathField.clear();

        reviewerIdField.clear();
        reviewNoteArea.clear();
    }

    // Create Medical object using form data

    private Medical buildMedicalFromForm() {

        int studentId = requiredId(
                studentIdField,
                "Student ID"
        );

        Integer offeringId = optionalId(
                offeringIdField,
                "Offering ID"
        );

        return new Medical(
                studentId,
                offeringId,
                fromDatePicker.getValue(),
                toDatePicker.getValue(),
                reasonArea.getText(),
                documentPathField.getText()
        );
    }

    // Display selected medical in form

    private void displayMedical(Medical medical) {

        medicalIdField.setText(
                String.valueOf(medical.getMedicalId())
        );

        studentIdField.setText(
                String.valueOf(medical.getStudentId())
        );

        offeringIdField.setText(
                medical.getOfferingId() == null
                        ? ""
                        : String.valueOf(medical.getOfferingId())
        );

        fromDatePicker.setValue(
                medical.getFromDate()
        );

        toDatePicker.setValue(
                medical.getToDate()
        );

        reasonArea.setText(
                safeText(medical.getReason())
        );

        documentPathField.setText(
                safeText(medical.getDocumentPath())
        );

        reviewNoteArea.setText(
                safeText(medical.getReviewNote())
        );
    }

    // Add or refresh one table row

    private void updateTableRow(Medical medical) {

        if (medical == null) {
            return;
        }

        for (int i = 0; i < medicalRecords.size(); i++) {

            if (medicalRecords.get(i).getMedicalId()
                    == medical.getMedicalId()) {

                medicalRecords.set(i, medical);

                medicalTable.getSelectionModel()
                        .select(medical);

                return;
            }
        }

        medicalRecords.add(0, medical);

        medicalTable.getSelectionModel()
                .select(medical);
    }

    // Required positive ID validation

    private int requiredId(
            TextField field,
            String fieldName) {

        try {

            int id = Integer.parseInt(
                    field.getText().trim()
            );

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Enter a valid " + fieldName + "."
            );
        }
    }

    // Optional positive ID validation

    private Integer optionalId(
            TextField field,
            String fieldName) {

        if (field.getText() == null
                || field.getText().isBlank()) {
            return null;
        }

        return requiredId(field, fieldName);
    }

    // Execute database work outside JavaFX UI thread

    private <T> void runTask(
            Callable<T> operation,
            Consumer<T> onSuccess) {

        medicalRoot.setDisable(true);
        statusLabel.setText("Processing...");

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };

        task.setOnSucceeded(event -> {

            medicalRoot.setDisable(false);
            onSuccess.accept(task.getValue());
        });

        task.setOnFailed(event -> {

            medicalRoot.setDisable(false);

            Throwable error = task.getException();

            String message =
                    error == null
                            ? "Unknown database error."
                            : error.getMessage();

            showError(message);
        });

        Thread thread = new Thread(
                task,
                "medical-database-task"
        );

        thread.setDaemon(true);
        thread.start();
    }

    // Prevent null text values

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    // Show error message

    private void showError(String message) {

        String text = message == null
                || message.isBlank()
                ? "An unexpected error occurred."
                : message;

        statusLabel.setText("Error: " + text);

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        alert.setTitle("Medical Management Error");
        alert.setHeaderText(null);
        alert.setContentText(text);

        alert.showAndWait();
    }
}
