
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import lk.ac.ruhuna.ftms.model.Medical;
import lk.ac.ruhuna.ftms.service.MedicalService;
import lk.ac.ruhuna.ftms.service.MedicalAttendanceLinkService;
import lk.ac.ruhuna.ftms.dao.MedicalAttendanceLinkDAO.AbsenceRow;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MedicalController {

    @FXML private BorderPane medicalRoot;

    // MEDICAL FORM
    @FXML private TextField medicalIdField;
    @FXML private TextField studentIdField;
    @FXML private TextField offeringIdField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TextArea reasonArea;
    @FXML private TextField documentPathField;

    // MEDICAL REVIEW
    @FXML private TextField reviewerIdField;
    @FXML private TextArea reviewNoteArea;

    // MEDICAL TABLE
    @FXML private TableView<Medical> medicalTable;
    @FXML private TableColumn<Medical, Integer> medicalIdColumn;
    @FXML private TableColumn<Medical, Integer> studentIdColumn;
    @FXML private TableColumn<Medical, String> periodColumn;
    @FXML private TableColumn<Medical, String> reasonColumn;
    @FXML private TableColumn<Medical, String> statusColumn;
    @FXML private Label statusLabel;

    // MEDICAL-ATTENDANCE LINKING
    @FXML private VBox medicalLinkSection;
    @FXML private TableView<AbsenceRow> absenceTable;
    @FXML private TableColumn<AbsenceRow, Integer> absenceIdColumn;
    @FXML private TableColumn<AbsenceRow, String> absenceDateColumn;
    @FXML private TableColumn<AbsenceRow, String> absenceTypeColumn;
    @FXML private TableColumn<AbsenceRow, String> absenceStatusColumn;
    @FXML private TableColumn<AbsenceRow, String> linkedMedicalColumn;

    private final MedicalService medicalService =
            new MedicalService();

    private final MedicalAttendanceLinkService linkService =
            new MedicalAttendanceLinkService();

    private final ObservableList<Medical> medicalRecords =
            FXCollections.observableArrayList();

    private final ObservableList<AbsenceRow> absenceRows =
            FXCollections.observableArrayList();

    // =====================================
    // INITIALIZE CONTROLLER
    // =====================================

    @FXML
    public void initialize() {

        medicalIdColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(
                        cell.getValue().getMedicalId()
                ).asObject());

        studentIdColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(
                        cell.getValue().getStudentId()
                ).asObject());

        periodColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().getFromDate()
                                + " to "
                                + cell.getValue().getToDate()));

        reasonColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        safeText(cell.getValue().getReason())));

        statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        safeText(cell.getValue().getStatus())));

        medicalTable.setItems(medicalRecords);

        // ATTENDANCE LINKING TABLE
        absenceIdColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(
                        cell.getValue().attendanceRecordId()
                ).asObject());

        absenceDateColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().sessionDate().toString()));

        absenceTypeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().sessionType()));

        absenceStatusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().attendanceStatus()));

        linkedMedicalColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().linkedMedicalId() == null
                                ? "Not linked"
                                : "Medical #"
                                + cell.getValue().linkedMedicalId()));

        absenceTable.setItems(absenceRows);

        // Only Technical Officers can link medicals
        var current = SessionManager.getCurrentUser();

        boolean canLink =
                current != null
                        && current.isActive()
                        && "TECHNICAL_OFFICER".equals(
                        current.getRole());

        medicalLinkSection.setVisible(canLink);
        medicalLinkSection.setManaged(canLink);

        medicalTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, selected) -> {

                    if (selected != null) {
                        displayMedical(selected);
                        absenceRows.clear();
                    }
                });

        // FIX 1: Get reviewer ID from login session
        reviewerIdField.setText(
                String.valueOf(
                        SessionManager.requireSession().userId()));

        // Prevent changing reviewer ID
        reviewerIdField.setEditable(false);

        statusLabel.setText("Medical Management Ready");
    }

    // =====================================
    // BACK TO DASHBOARD
    // =====================================

    @FXML
    private void handleBack() {
        try {
            NavigationUtil.navigate(
                    medicalRoot,
                    NavigationUtil.dashboardResource(),
                    "FTMS - Dashboard"
            );
        } catch (IOException | RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // SUBMIT MEDICAL
    // =====================================

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
                                        + saved.getMedicalId());
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // UPDATE PENDING MEDICAL
    // =====================================

    @FXML
    private void handleUpdateMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            Medical medical = buildMedicalFromForm();

            medical.setMedicalId(medicalId);

            runTask(
                    () -> {

                        boolean updated =
                                medicalService.updatePendingMedical(
                                        medical);

                        if (!updated) {
                            throw new IllegalStateException(
                                    "Medical could not be updated.");
                        }

                        return medicalService.getMedicalById(
                                medicalId);
                    },

                    updated -> {
                        updateTableRow(updated);

                        statusLabel.setText(
                                "Medical updated successfully.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // FIND MEDICAL
    // =====================================

    @FXML
    private void handleFindMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            runTask(
                    () -> medicalService.getMedicalById(
                            medicalId),

                    medical -> {

                        medicalRecords.clear();

                        if (medical == null) {
                            statusLabel.setText(
                                    "Medical record not found.");
                            return;
                        }

                        medicalRecords.add(medical);

                        medicalTable.getSelectionModel()
                                .select(medical);

                        statusLabel.setText(
                                "Medical record found.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // LOAD STUDENT MEDICALS
    // =====================================

    @FXML
    private void handleLoadStudentMedicals() {

        try {

            int studentId = requiredId(
                    studentIdField, "Student ID");

            runTask(
                    () -> medicalService.getMedicalsByStudent(
                            studentId),

                    records -> {
                        medicalRecords.setAll(records);

                        statusLabel.setText(
                                "Loaded " + records.size()
                                        + " medical records.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // LOAD ALL MEDICALS
    // =====================================

    @FXML
    private void handleLoadAllMedicals() {

        runTask(
                medicalService::getAllMedicals,

                records -> {
                    medicalRecords.setAll(records);

                    statusLabel.setText(
                            "Loaded " + records.size()
                                    + " medical records.");
                }
        );
    }

    // =====================================
    // APPROVE OR REJECT MEDICAL
    // =====================================

    @FXML
    private void handleApproveMedical() {
        reviewMedical(true);
    }

    @FXML
    private void handleRejectMedical() {
        reviewMedical(false);
    }

    private void reviewMedical(boolean approve) {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            int reviewerId = requiredId(
                    reviewerIdField, "Reviewer ID");

            String note = reviewNoteArea.getText();

            runTask(
                    () -> {

                        if (approve) {

                            medicalService.approveMedical(
                                    medicalId,
                                    reviewerId,
                                    note);

                        } else {

                            medicalService.rejectMedical(
                                    medicalId,
                                    reviewerId,
                                    note);
                        }

                        return medicalService.getMedicalById(
                                medicalId);
                    },

                    reviewed -> {
                        updateTableRow(reviewed);

                        statusLabel.setText(
                                approve
                                        ? "Medical approved successfully."
                                        : "Medical rejected successfully.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // DELETE PENDING MEDICAL
    // =====================================

    @FXML
    private void handleDeleteMedical() {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            int studentId = requiredId(
                    studentIdField, "Student ID");

            runTask(
                    () -> {

                        boolean deleted =
                                medicalService.deletePendingMedical(
                                        medicalId,
                                        studentId);

                        if (!deleted) {
                            throw new IllegalStateException(
                                    "Medical could not be deleted.");
                        }

                        return medicalId;
                    },

                    deletedId -> {

                        medicalRecords.removeIf(medical ->
                                medical.getMedicalId() == deletedId);

                        clearForm();

                        statusLabel.setText(
                                "Medical deleted successfully.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // LOAD MATCHING ABSENCES
    // =====================================

    @FXML
    private void handleLoadMedicalAbsences() {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            runTask(
                    () -> linkService.getCandidateAbsences(
                            medicalId),

                    rows -> {
                        absenceRows.setAll(rows);

                        statusLabel.setText(
                                rows.isEmpty()
                                        ? "No matching absent attendance records."
                                        : "Loaded " + rows.size()
                                        + " matching attendance records.");
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // LINK / UNLINK ATTENDANCE ABSENCE
    // =====================================

    @FXML
    private void handleLinkMedicalAbsence() {
        changeMedicalAbsenceLink(true);
    }

    @FXML
    private void handleUnlinkMedicalAbsence() {
        changeMedicalAbsenceLink(false);
    }

    private void changeMedicalAbsenceLink(boolean link) {

        try {

            int medicalId = requiredId(
                    medicalIdField, "Medical ID");

            AbsenceRow selected =
                    absenceTable.getSelectionModel()
                            .getSelectedItem();

            if (selected == null) {
                throw new IllegalArgumentException(
                        "Select an attendance row first.");
            }

            int attendanceRecordId =
                    selected.attendanceRecordId();

            runTask(
                    () -> {

                        if (link) {
                            linkService.linkAbsence(
                                    medicalId,
                                    attendanceRecordId);
                        } else {
                            linkService.unlinkAbsence(
                                    medicalId,
                                    attendanceRecordId);
                        }

                        return linkService.getCandidateAbsences(
                                medicalId);
                    },

                    rows -> {
                        absenceRows.setAll(rows);

                        statusLabel.setText(
                                link
                                        ? "Medical linked to attendance record #"
                                        + attendanceRecordId
                                        : "Medical link removed from attendance record #"
                                        + attendanceRecordId);
                    }
            );

        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    // =====================================
    // BROWSE MEDICAL DOCUMENT
    // =====================================

    @FXML
    private void handleBrowseDocument() {

        FileChooser chooser = new FileChooser();

        chooser.setTitle("Select Medical Document");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Documents",
                        "*.pdf", "*.jpg", "*.jpeg", "*.png")
        );

        File selected = chooser.showOpenDialog(
                documentPathField.getScene().getWindow());

        if (selected != null) {
            documentPathField.setText(
                    selected.getAbsolutePath());
        }
    }

    // =====================================
    // CLEAR FORM
    // =====================================

    @FXML
    private void handleClear() {

        medicalTable.getSelectionModel().clearSelection();
        absenceRows.clear();

        clearForm();

        statusLabel.setText("Form cleared.");
    }

    private void clearForm() {

        absenceRows.clear();

        medicalIdField.clear();
        studentIdField.clear();
        offeringIdField.clear();

        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);

        reasonArea.clear();
        documentPathField.clear();

        // Keep reviewer ID equal to logged-in user
        reviewerIdField.setText(
                String.valueOf(
                        SessionManager.requireSession().userId()));

        reviewNoteArea.clear();
    }

    // =====================================
    // BUILD MEDICAL FROM FORM
    // =====================================

    private Medical buildMedicalFromForm() {

        int studentId = requiredId(
                studentIdField, "Student ID");

        Integer offeringId = optionalId(
                offeringIdField, "Offering ID");

        return new Medical(
                studentId,
                offeringId,
                fromDatePicker.getValue(),
                toDatePicker.getValue(),
                reasonArea.getText(),
                documentPathField.getText());
    }

    // =====================================
    // DISPLAY SELECTED MEDICAL
    // =====================================

    private void displayMedical(Medical medical) {

        medicalIdField.setText(
                String.valueOf(medical.getMedicalId()));

        studentIdField.setText(
                String.valueOf(medical.getStudentId()));

        offeringIdField.setText(
                medical.getOfferingId() == null
                        ? ""
                        : String.valueOf(medical.getOfferingId()));

        fromDatePicker.setValue(
                medical.getFromDate());

        toDatePicker.setValue(
                medical.getToDate());

        reasonArea.setText(
                safeText(medical.getReason()));

        documentPathField.setText(
                safeText(medical.getDocumentPath()));

        reviewNoteArea.setText(
                safeText(medical.getReviewNote()));
    }

    // =====================================
    // UPDATE MEDICAL TABLE ROW
    // =====================================

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

        medicalTable.getSelectionModel().select(medical);
    }

    // =====================================
    // VALIDATE REQUIRED ID
    // =====================================

    private int requiredId(
            TextField field,
            String fieldName) {

        try {

            int id = Integer.parseInt(
                    field.getText().trim());

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Enter a valid " + fieldName + ".");
        }
    }

    // =====================================
    // VALIDATE OPTIONAL ID
    // =====================================

    private Integer optionalId(
            TextField field,
            String fieldName) {

        if (field.getText() == null
                || field.getText().isBlank()) {
            return null;
        }

        return requiredId(field, fieldName);
    }

    // =====================================
    // BACKGROUND DATABASE TASK
    // =====================================

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

            String message = error == null
                    ? "Unknown database error."
                    : error.getMessage();

            showError(message);
        });

        Thread thread = new Thread(
                task,
                "medical-database-task");

        thread.setDaemon(true);
        thread.start();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    // =====================================
    // ERROR ALERT
    // =====================================

    private void showError(String message) {

        String text = message == null
                || message.isBlank()
                ? "An unexpected error occurred."
                : message;

        statusLabel.setText("Error: " + text);

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Medical Management Error");
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}
