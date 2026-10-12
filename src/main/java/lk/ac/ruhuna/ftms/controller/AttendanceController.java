
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import lk.ac.ruhuna.ftms.model.AttendanceRecord;
import lk.ac.ruhuna.ftms.model.AttendanceSession;
import lk.ac.ruhuna.ftms.model.AttendanceSummary;
import lk.ac.ruhuna.ftms.service.AttendanceService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public class AttendanceController {

    // Existing Attendance Session controls

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

    // Existing Attendance Record controls

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

    // New Attendance Summary controls

    @FXML
    private TableView<AttendanceSummary> summaryTable;

    @FXML
    private TableColumn<AttendanceSummary, Integer>
            summaryEnrollmentColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryRegistrationColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryStudentColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryTheoryColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryPracticalColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryCombinedColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryMedicalColumn;

    @FXML
    private TableColumn<AttendanceSummary, String>
            summaryUnmarkedColumn;

    @FXML
    private Label summaryDetailsLabel;

    private final AttendanceService attendanceService =
            new AttendanceService();

    private final ObservableList<AttendanceRecord> attendanceRecords =
            FXCollections.observableArrayList();

    private final ObservableList<AttendanceSummary> summaryRows =
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
                data -> new SimpleIntegerProperty(
                        data.getValue().getEnrollmentId()
                ).asObject()
        );

        statusColumn.setCellValueFactory(
                data -> new SimpleStringProperty(
                        data.getValue().getAttendanceStatus()
                )
        );

        attendanceTable.setItems(attendanceRecords);

        // Step 5 will add the new controls to attendance.fxml.
        // Keep the existing screen working until then.
        if (summaryTable != null) {
            initializeSummaryTable();
        }
    }

    // NEW: Setup Attendance Summary table

    private void initializeSummaryTable() {

        summaryEnrollmentColumn.setCellValueFactory(
                cell -> new SimpleIntegerProperty(
                        cell.getValue().getEnrollmentId()
                ).asObject()
        );

        summaryRegistrationColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getRegistrationNo()
                )
        );

        summaryStudentColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        cell.getValue().getStudentName()
                )
        );

        summaryTheoryColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getTheoryPresent(),
                                cell.getValue().getTheorySessions(),
                                cell.getValue().getTheoryPercentage()
                        )
                )
        );

        summaryPracticalColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getPracticalPresent(),
                                cell.getValue().getPracticalSessions(),
                                cell.getValue().getPracticalPercentage()
                        )
                )
        );

        summaryCombinedColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        progress(
                                cell.getValue().getTotalPresent(),
                                cell.getValue().getTotalSessions(),
                                cell.getValue().getCombinedPercentage()
                        )
                )
        );

        summaryMedicalColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getMedicalCount()
                        )
                )
        );

        summaryUnmarkedColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getUnmarkedCount()
                        )
                )
        );

        summaryTable.setItems(summaryRows);

        // Show student details when a row is selected.

        summaryTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {

                    if (selected != null) {

                        enrollmentIdField.setText(
                                String.valueOf(
                                        selected.getEnrollmentId()
                                )
                        );

                        summaryDetailsLabel.setText(
                                selected.getRegistrationNo()
                                        + " - "
                                        + selected.getStudentName()

                                        + " | Theory: "
                                        + formatPercentage(
                                        selected.getTheoryPercentage()
                                )

                                        + " | Practical: "
                                        + formatPercentage(
                                        selected.getPracticalPercentage()
                                )

                                        + " | Combined: "
                                        + formatPercentage(
                                        selected.getCombinedPercentage()
                                )

                                        + " | Late: "
                                        + selected.getLateCount()

                                        + " | Excused: "
                                        + selected.getExcusedCount()

                                        + " | Medical: "
                                        + selected.getMedicalCount()

                                        + " | Unmarked: "
                                        + selected.getUnmarkedCount()
                        );

                    } else {

                        summaryDetailsLabel.setText(
                                "Select a student to view individual attendance details."
                        );
                    }
                });
    }

    // Existing: Save Attendance Session

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
                        Integer.parseInt(
                                conductedByField.getText()
                        );
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

            // Previously loaded summary is now outdated.
            summaryRows.clear();

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

    // Existing: Save Student Attendance

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

            // Reload summary after attendance changes.
            summaryRows.clear();

            statusLabel.setText(
                    "Attendance saved successfully."
            );

            loadAttendanceRecords();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // Existing: Load Attendance Records

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

    // NEW: Load Attendance Summary for a Course Offering

    @FXML
    private void handleLoadSummary() {

        try {

            int offeringId =
                    Integer.parseInt(
                            offeringIdField.getText().trim()
                    );

            summaryRows.setAll(
                    attendanceService.getSummariesByOffering(
                            offeringId
                    )
            );

            if (summaryRows.isEmpty()) {

                statusLabel.setText(
                        "No enrollments found for offering "
                                + offeringId
                );

            } else {

                statusLabel.setText(
                        "Loaded "
                                + summaryRows.size()
                                + " student summaries. Select a row for details."
                );
            }

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // NEW: Format attendance percentage

    private static String formatPercentage(Double percentage) {

        if (percentage == null) {
            return "N/A";
        }

        return String.format(
                Locale.ROOT,
                "%.2f%%",
                percentage
        );
    }

    // NEW: Format present/total progress

    private static String progress(
            int present,
            int sessions,
            Double percentage) {

        return present
                + "/"
                + sessions
                + " ("
                + formatPercentage(percentage)
                + ")";
    }

    // Existing: Clear Form

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
        summaryRows.clear();

        if (summaryDetailsLabel != null) {

            summaryDetailsLabel.setText(
                    "Select a student to view individual attendance details."
            );
        }

        statusLabel.setText("");
    }

    // Existing: Display Error Message

    private void showError(String message) {

        Alert alert =
                new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Attendance Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }
}
