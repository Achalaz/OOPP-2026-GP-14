package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.service.MarkService;

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