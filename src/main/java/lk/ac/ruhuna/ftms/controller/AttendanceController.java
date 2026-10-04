package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.model.AttendanceRecord;
import lk.ac.ruhuna.ftms.model.AttendanceSession;
import lk.ac.ruhuna.ftms.service.AttendanceService;

import java.time.LocalDate;
import java.time.LocalTime;

public class AttendanceController {

    @FXML
    private TextField offeringIdField;

    @FXML
    private DatePicker sessionDatePicker;

    @FXML
    private TextField startTimeField;

    @FXML
    private TextField endTimeField;

    @FXML
    private ComboBox<String> sessionTypeCombo;

    @FXML
    private TextField topicField;

    @FXML
    private TextField conductedByField;

    @FXML
    private TextField sessionIdField;

    @FXML
    private TextField enrollmentIdField;

    @FXML
    private ComboBox<String> attendanceStatusCombo;

    @FXML
    private TableView<AttendanceRecord> attendanceTable;

    @FXML
    private TableColumn<AttendanceRecord, Integer> enrollmentColumn;

    @FXML
    private TableColumn<AttendanceRecord, String> statusColumn;

    @FXML
    private Label statusLabel;

    private final AttendanceService attendanceService =
            new AttendanceService();

    private final ObservableList<AttendanceRecord> attendanceRecords =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {

        sessionTypeCombo.setItems(
                FXCollections.observableArrayList(
                        "THEORY",
                        "PRACTICAL",
                        "TUTORIAL",
                        "OTHER"
                )
        );

        attendanceStatusCombo.setItems(
                FXCollections.observableArrayList(
                        "PRESENT",
                        "ABSENT",
                        "LATE",
                        "EXCUSED",
                        "MEDICAL"
                )
        );

        enrollmentColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleIntegerProperty(
                        data.getValue().getEnrollmentId()
                ).asObject()
        );

        statusColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getAttendanceStatus()
                )
        );

        attendanceTable.setItems(attendanceRecords);
    }

    @FXML
    private void handleSaveSession() {

        try {

            int offeringId =
                    Integer.parseInt(offeringIdField.getText());

            LocalDate sessionDate =
                    sessionDatePicker.getValue();

            LocalTime startTime =
                    LocalTime.parse(startTimeField.getText());

            LocalTime endTime =
                    LocalTime.parse(endTimeField.getText());

            String sessionType =
                    sessionTypeCombo.getValue();

            String topic =
                    topicField.getText();

            Integer conductedBy = null;

            if (!conductedByField.getText().isBlank()) {
                conductedBy =
                        Integer.parseInt(conductedByField.getText());
            }

            AttendanceSession session =
                    new AttendanceSession(
                            offeringId,
                            sessionDate,
                            startTime,
                            endTime,
                            sessionType,
                            topic,
                            conductedBy
                    );

            attendanceService.saveSession(session);

            sessionIdField.setText(
                    String.valueOf(session.getSessionId())
            );

            statusLabel.setText(
                    "Attendance session saved successfully."
            );

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleSaveAttendance() {

        try {

            int sessionId =
                    Integer.parseInt(sessionIdField.getText());

            int enrollmentId =
                    Integer.parseInt(enrollmentIdField.getText());

            String status =
                    attendanceStatusCombo.getValue();

            AttendanceRecord record =
                    new AttendanceRecord(
                            sessionId,
                            enrollmentId,
                            status,
                            null
                    );

            attendanceService.saveAttendance(record);

            statusLabel.setText(
                    "Attendance saved successfully."
            );

            loadAttendanceRecords();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleLoadAttendance() {

        try {
            loadAttendanceRecords();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void loadAttendanceRecords() {

        int sessionId =
                Integer.parseInt(sessionIdField.getText());

        attendanceRecords.setAll(
                attendanceService.getAttendanceBySession(sessionId)
        );
    }

    @FXML
    private void handleClear() {

        offeringIdField.clear();
        sessionDatePicker.setValue(null);
        startTimeField.clear();
        endTimeField.clear();
        sessionTypeCombo.setValue(null);
        topicField.clear();
        conductedByField.clear();

        sessionIdField.clear();
        enrollmentIdField.clear();
        attendanceStatusCombo.setValue(null);

        attendanceRecords.clear();

        statusLabel.setText("");
    }

    private void showError(String message) {

        Alert alert =
                new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Attendance Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }
}