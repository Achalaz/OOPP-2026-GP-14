
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
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.TechnicalOfficerMedicalService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Technical Officer Medical Management.
 * Only medical records within the officer's
 * authorized department are accessible.
 */
public final class MedicalController {

    // ======================================
    // FXML FIELDS
    // ======================================

    @FXML private BorderPane medicalRoot;

    @FXML private TextField medicalIdField;
    @FXML private TextField studentIdField;
    @FXML private TextField offeringIdField;

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;

    @FXML private TextArea reasonArea;
    @FXML private TextField documentPathField;
    @FXML private TextArea reviewNoteArea;

    @FXML private TableView<Medical> medicalTable;

    @FXML private TableColumn<Medical, Integer> medicalIdColumn;
    @FXML private TableColumn<Medical, Integer> studentIdColumn;
    @FXML private TableColumn<Medical, String> periodColumn;
    @FXML private TableColumn<Medical, String> reasonColumn;
    @FXML private TableColumn<Medical, String> statusColumn;

    @FXML private Label statusLabel;

    // ======================================
    // SERVICE AND SESSION
    // ======================================

    private final ObservableList<Medical> records =
            FXCollections.observableArrayList();

    private SessionIdentity loginIdentity;

    private TechnicalOfficerMedicalService service;

    private boolean busy;

    // ======================================
    // INITIALIZE
    // ======================================

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        requireOfficer();

        service = new TechnicalOfficerMedicalService();

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
                        safe(cell.getValue().getReason())
                )
        );

        statusColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        safe(cell.getValue().getStatus())
                )
        );

        medicalTable.setItems(records);

        medicalTable.setPlaceholder(
                new Label("No medical records found.")
        );

        medicalTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldRow, row) -> {

                    if (row != null) {
                        displayMedical(row);
                    }
                });

        handleLoadAllMedicals();
    }

    // ======================================
    // TECHNICAL OFFICER AUTHORIZATION
    // ======================================

    private User requireOfficer() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "Active Technical Officer login required."
            );
        }

        return user;
    }

    // ======================================
    // CREATE MEDICAL
    // ======================================

    @FXML
    private void handleSubmitMedical() {

        try {

            requireOfficer();

            if (medicalIdField.getText() != null
                    && !medicalIdField.getText().isBlank()) {

                throw new IllegalArgumentException(
                        "Clear the form before creating a new medical record."
                );
            }

            Medical medical = readMedicalForm();

            runTask(
                    "Saving medical...",

                    () -> service.createMedical(medical),

                    saved -> {

                        updateTableRow(saved);

                        statusLabel.setText(
                                "Created medical #"
                                        + saved.getMedicalId()
                        );
                    }
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // UPDATE PENDING MEDICAL
    // ======================================

    @FXML
    private void handleUpdateMedical() {

        try {

            requireOfficer();

            int id = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            Medical medical = readMedicalForm();

            medical.setMedicalId(id);

            runTask(
                    "Updating medical...",

                    () -> service.updatePendingMedical(medical),

                    updated -> {

                        updateTableRow(updated);

                        statusLabel.setText(
                                "Updated medical #" + id
                        );
                    }
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // FIND MEDICAL BY ID
    // ======================================

    @FXML
    private void handleFindMedical() {

        try {

            requireOfficer();

            int id = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            runTask(
                    "Finding medical...",

                    () -> service.getMedical(id),

                    result -> {

                        medicalTable.getSelectionModel()
                                .clearSelection();

                        records.clear();

                        if (result == null) {

                            statusLabel.setText(
                                    "Medical not found in your department."
                            );

                        } else {

                            records.add(result);

                            medicalTable.getSelectionModel()
                                    .select(result);

                            statusLabel.setText(
                                    "Medical #" + id + " loaded."
                            );
                        }
                    }
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // LOAD STUDENT MEDICALS
    // ======================================

    @FXML
    private void handleLoadStudentMedicals() {

        try {

            requireOfficer();

            int id = requiredId(
                    studentIdField,
                    "Student ID"
            );

            runTask(
                    "Loading student medicals...",

                    () -> service.getStudentMedicals(id),

                    this::displayRecords
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // LOAD DEPARTMENT MEDICALS
    // ======================================

    @FXML
    private void handleLoadAllMedicals() {

        runTask(
                "Loading department medicals...",

                service::getDepartmentMedicals,

                this::displayRecords
        );
    }

    // ======================================
    // APPROVE MEDICAL
    // ======================================

    @FXML
    private void handleApproveMedical() {

        review(true);
    }

    // ======================================
    // REJECT MEDICAL
    // ======================================

    @FXML
    private void handleRejectMedical() {

        review(false);
    }

    // ======================================
    // COMMON REVIEW METHOD
    // ======================================

    private void review(boolean approve) {

        try {

            requireOfficer();

            int id = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            String action = approve ? "approve" : "reject";

            if (!confirm(
                    "Do you want to "
                            + action
                            + " medical #"
                            + id
                            + "?")) {

                return;
            }

            String note = reviewNoteArea.getText();

            runTask(
                    "Reviewing medical...",

                    () -> approve
                            ? service.approveMedical(id, note)
                            : service.rejectMedical(id, note),

                    reviewed -> {

                        updateTableRow(reviewed);

                        statusLabel.setText(
                                "Medical #" + id
                                        + (approve
                                        ? " approved."
                                        : " rejected.")
                        );
                    }
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // DELETE PENDING MEDICAL
    // ======================================

    @FXML
    private void handleDeleteMedical() {

        try {

            requireOfficer();

            int id = requiredId(
                    medicalIdField,
                    "Medical ID"
            );

            if (!confirm(
                    "Permanently delete PENDING medical #"
                            + id + "?")) {

                return;
            }

            runTask(
                    "Deleting medical...",

                    () -> {

                        service.deletePendingMedical(id);

                        return id;
                    },

                    deleted -> {

                        records.removeIf(
                                m -> m.getMedicalId() == deleted
                        );

                        medicalTable.getSelectionModel()
                                .clearSelection();

                        clearForm();

                        statusLabel.setText(
                                "Deleted medical #" + deleted
                        );
                    }
            );

        } catch (IllegalArgumentException | SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // BROWSE MEDICAL DOCUMENT
    // ======================================

    @FXML
    private void handleBrowseDocument() {

        try {

            requireOfficer();

            FileChooser chooser = new FileChooser();

            chooser.setTitle("Select Medical Document");

            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "PDF and image files",
                            "*.pdf",
                            "*.png",
                            "*.jpg",
                            "*.jpeg"
                    )
            );

            File selected = chooser.showOpenDialog(
                    medicalRoot.getScene() == null
                            ? null
                            : medicalRoot.getScene().getWindow()
            );

            if (selected != null) {

                documentPathField.setText(
                        selected.getAbsolutePath()
                );
            }

        } catch (SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // CLEAR FORM
    // ======================================

    @FXML
    private void handleClear() {

        try {

            requireOfficer();

            medicalTable.getSelectionModel()
                    .clearSelection();

            clearForm();

            statusLabel.setText("Form cleared.");

        } catch (SecurityException error) {

            showError(error.getMessage());
        }
    }

    // ======================================
    // BACK TO TO DASHBOARD
    // ======================================

    @FXML
    private void handleBack() {

        try {

            requireOfficer();

            NavigationUtil.navigate(
                    medicalRoot,
                    "/fxml/technical_officer_dashboard.fxml",
                    "FTMS - Technical Officer Dashboard"
            );

        } catch (IOException | RuntimeException error) {

            showError(
                    "Unable to open Technical Officer Dashboard: "
                            + error.getMessage()
            );
        }
    }

    // ======================================
    // READ FORM DATA
    // ======================================

    private Medical readMedicalForm() {

        int studentId = requiredId(
                studentIdField,
                "Student ID"
        );

        Integer offeringId = optionalId(
                offeringIdField,
                "Offering ID"
        );

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        if (from == null
                || to == null
                || from.isAfter(to)) {

            throw new IllegalArgumentException(
                    "Select a valid From Date and To Date."
            );
        }

        return new Medical(
                studentId,
                offeringId,
                from,
                to,
                reasonArea.getText(),
                documentPathField.getText()
        );
    }

    // ======================================
    // DISPLAY SELECTED MEDICAL
    // ======================================

    private void displayMedical(Medical medical) {

        medicalIdField.setText(
                Integer.toString(medical.getMedicalId())
        );

        studentIdField.setText(
                Integer.toString(medical.getStudentId())
        );

        offeringIdField.setText(
                medical.getOfferingId() == null
                        ? ""
                        : medical.getOfferingId().toString()
        );

        fromDatePicker.setValue(
                medical.getFromDate()
        );

        toDatePicker.setValue(
                medical.getToDate()
        );

        reasonArea.setText(
                safe(medical.getReason())
        );

        documentPathField.setText(
                safe(medical.getDocumentPath())
        );

        reviewNoteArea.setText(
                safe(medical.getReviewNote())
        );
    }

    // ======================================
    // DISPLAY MEDICAL RECORDS
    // ======================================

    private void displayRecords(List<Medical> items) {

        medicalTable.getSelectionModel()
                .clearSelection();

        records.setAll(items);

        statusLabel.setText(
                "Loaded " + items.size()
                        + " department medical record(s)."
        );
    }

    // ======================================
    // ADD OR REPLACE TABLE ROW
    // ======================================

    private void updateTableRow(Medical medical) {

        if (medical == null) {
            return;
        }

        for (int i = 0; i < records.size(); i++) {

            if (records.get(i).getMedicalId()
                    == medical.getMedicalId()) {

                records.set(i, medical);

                medicalTable.getSelectionModel()
                        .select(medical);

                return;
            }
        }

        records.add(0, medical);

        medicalTable.getSelectionModel()
                .select(medical);
    }

    // ======================================
    // ID VALIDATION
    // ======================================

    private static int requiredId(
            TextField field,
            String label) {

        try {

            int id = Integer.parseInt(
                    field.getText().strip()
            );

            if (id > 0) {
                return id;
            }

        } catch (NullPointerException | NumberFormatException ignored) {
            // Use the consistent validation message below.
        }

        throw new IllegalArgumentException(
                "Enter a valid positive " + label + "."
        );
    }

    private static Integer optionalId(
            TextField field,
            String label) {

        if (field.getText() == null
                || field.getText().isBlank()) {

            return null;
        }

        return requiredId(field, label);
    }

    private void clearForm() {

        medicalIdField.clear();
        studentIdField.clear();
        offeringIdField.clear();

        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);

        reasonArea.clear();
        documentPathField.clear();
        reviewNoteArea.clear();
    }

    // ======================================
    // DATABASE BACKGROUND TASK
    // ======================================

    private <T> void runTask(
            String message,
            Callable<T> work,
            Consumer<T> onSuccess) {

        if (busy) {
            return;
        }

        try {

            requireOfficer();

        } catch (SecurityException error) {

            showError(error.getMessage());
            return;
        }

        busy = true;

        medicalRoot.setDisable(true);

        statusLabel.setText(message);

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {

                return work.call();
            }
        };

        task.setOnSucceeded(event -> {

            busy = false;
            medicalRoot.setDisable(false);

            try {

                requireOfficer();

                onSuccess.accept(task.getValue());

            } catch (SecurityException error) {

                records.clear();
                clearForm();

                showError(error.getMessage());

            } catch (RuntimeException error) {

                showError(error.getMessage());
            }
        });

        task.setOnFailed(event -> {

            busy = false;
            medicalRoot.setDisable(false);

            Throwable error = task.getException();

            if (error != null) {
                error.printStackTrace();
            }

            if (error instanceof SecurityException) {

                records.clear();
                clearForm();
            }

            showError(
                    error == null
                            ? "Unknown database error."
                            : error.getMessage()
            );
        });

        Thread worker = new Thread(
                task,
                "ftms-officer-medicals"
        );

        worker.setDaemon(true);
        worker.start();
    }

    // ======================================
    // CONFIRM ACTION
    // ======================================

    private boolean confirm(String question) {

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                question,
                ButtonType.YES,
                ButtonType.NO
        );

        alert.setTitle("Confirm Medical Action");
        alert.setHeaderText(null);

        return alert.showAndWait()
                .filter(ButtonType.YES::equals)
                .isPresent();
    }

    // ======================================
    // ERROR MESSAGE
    // ======================================

    private void showError(String message) {

        String text =
                message == null || message.isBlank()
                        ? "An unexpected error occurred."
                        : message;

        statusLabel.setText(
                "Error: " + text
        );

        Alert alert = new Alert(
                Alert.AlertType.ERROR,
                text,
                ButtonType.OK
        );

        alert.setTitle(
                "Technical Officer Medical Management"
        );

        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static String safe(String value) {

        return value == null ? "" : value;
    }
}
