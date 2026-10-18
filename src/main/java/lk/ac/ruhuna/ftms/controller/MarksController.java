package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.service.MarkService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;

public class MarksController {

    @FXML
    private TextField enrollmentIdField;

    @FXML
    private TextField assessmentIdField;

    @FXML
    private TextField markField;

    @FXML
    private TextField attemptNoField;

    @FXML
    private TableView<Mark> marksTable;

    @FXML
    private TableColumn<Mark, Integer> markIdColumn;

    @FXML
    private TableColumn<Mark, Integer> enrollmentColumn;

    @FXML
    private TableColumn<Mark, Integer> assessmentColumn;

    @FXML
    private TableColumn<Mark, Double> markColumn;

    @FXML
    private TableColumn<Mark, Integer> attemptColumn;

    @FXML
    private Button saveButton;

    @FXML
    private Button deleteButton;

    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();

    private final MarkService markService =
            new MarkService();

    @FXML
    public void initialize() {

        markIdColumn.setCellValueFactory(
                new PropertyValueFactory<>("markId")
        );

        enrollmentColumn.setCellValueFactory(
                new PropertyValueFactory<>("enrollmentId")
        );

        assessmentColumn.setCellValueFactory(
                new PropertyValueFactory<>("assessmentId")
        );

        markColumn.setCellValueFactory(
                new PropertyValueFactory<>("marksObtained")
        );

        attemptColumn.setCellValueFactory(
                new PropertyValueFactory<>("attemptNo")
        );

        loadMarks();

        boolean lecturer = "LECTURER".equals(SessionManager.getCurrentUser().getRole());
        saveButton.setDisable(!lecturer);
        deleteButton.setVisible(!lecturer);
        deleteButton.setManaged(!lecturer);
        if (lecturer) marksTable.setPlaceholder(new Label("No marks in your assigned courses."));

        marksTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, selected) -> {

                    if (selected != null) {
                        enrollmentIdField.setText(
                                String.valueOf(
                                        selected.getEnrollmentId()
                                )
                        );

                        assessmentIdField.setText(
                                String.valueOf(
                                        selected.getAssessmentId()
                                )
                        );

                        markField.setText(
                                selected.getMarksObtained() == null
                                        ? ""
                                        : String.valueOf(
                                        selected.getMarksObtained()
                                )
                        );

                        attemptNoField.setText(
                                String.valueOf(
                                        selected.getAttemptNo()
                                )
                        );
                    }
                });
    }

    @FXML
    private void handleSave() {

        try {

            SessionManager.requireSession(identity);

            int enrollmentId =
                    Integer.parseInt(
                            enrollmentIdField.getText().trim()
                    );

            int assessmentId =
                    Integer.parseInt(
                            assessmentIdField.getText().trim()
                    );

            double mark =
                    Double.parseDouble(
                            markField.getText().trim()
                    );

            int attemptNo =
                    Integer.parseInt(
                            attemptNoField.getText().trim()
                    );

            Mark newMark = new Mark(
                    enrollmentId,
                    assessmentId,
                    mark,
                    attemptNo
            );

            markService.saveMark(newMark);

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Success",
                    "Mark saved successfully."
            );

            clearFields();
            loadMarks();

        } catch (SecurityException e) {

            marksTable.getItems().clear();
            clearFields();
            showAlert(Alert.AlertType.ERROR, "Access Denied", e.getMessage());

        } catch (NumberFormatException e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Invalid Input",
                    "Please enter valid numeric values."
            );

        } catch (InvalidMarkException e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Validation Error",
                    e.getMessage()
            );

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Error",
                    "Unable to save mark."
            );

            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {

        Mark selected =
                marksTable.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Select Mark",
                    "Please select a mark first."
            );

            return;
        }

        try {

            markService.deleteMark(
                    selected.getMarkId()
            );

            loadMarks();
            clearFields();

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Error",
                    "Unable to delete mark."
            );
        }
    }

    @FXML
    private void handleClear() {
        clearFields();
    }

    @FXML
    private void handleEligibility() {
        openAcademicScreen("eligibility", "/fxml/eligibility.fxml", "FTMS - Eligibility");
    }

    @FXML
    private void handleResults() {
        openAcademicScreen("results", "/fxml/results.fxml", "FTMS - Results & Grades");
    }

    private void openAcademicScreen(String mode, String resource, String title) {
        try {
            SessionManager.requireSession(identity);
            if ("LECTURER".equals(SessionManager.getCurrentUser().getRole())) {
                // Keep the existing academic integrations within the lecturer's assigned courses.
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/lecturer_workspace.fxml"));
                Parent root = loader.load();
                marksTable.getScene().setRoot(root);
                LecturerWorkspaceController controller = loader.getController();
                controller.configure(mode, title);
            } else {
                NavigationUtil.navigate(marksTable, resource, title);
            }
        } catch (IOException | SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Navigation Error", e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDashboard() {
        try {
            SessionManager.requireSession(identity);
            NavigationUtil.navigate(marksTable, NavigationUtil.dashboardResource(), "FTMS - Dashboard");
        } catch (IOException | SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Navigation Error", e.getMessage());
        }
    }

    private void loadMarks() {

        marksTable.setItems(
                FXCollections.observableArrayList(
                        markService.getAllMarks()
                )
        );
    }

    private void clearFields() {

        enrollmentIdField.clear();
        assessmentIdField.clear();
        markField.clear();

        attemptNoField.setText("1");

        marksTable
                .getSelectionModel()
                .clearSelection();
    }

    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
