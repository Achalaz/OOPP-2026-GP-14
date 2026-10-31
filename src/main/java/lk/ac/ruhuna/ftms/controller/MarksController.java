package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.exception.PublishedResultProtectedException;
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

        initializeSelections();
        boolean lecturer = "LECTURER".equals(SessionManager.getCurrentUser().getRole());
        saveButton.setDisable(!lecturer);
        deleteButton.setVisible(!lecturer);
        deleteButton.setManaged(!lecturer);
        if (lecturer) marksTable.setPlaceholder(new Label("No marks in your assigned courses."));

        marksTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, selected) -> {

                    if (selected != null) {
                        markStatusCombo.setValue(selected.getMarkStatus());
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

            Double mark = markField.getText().isBlank() ? null : Double.valueOf(markField.getText().trim());

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

            newMark.setMarkStatus(markStatusCombo.getValue());
            markService.saveMark(newMark);

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Success",
                    "Mark saved successfully."
            );

            clearFields();
            loadMarks();

        } catch (PublishedResultProtectedException e) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Published Result",
                    "Marks cannot be changed because this enrollment's result is already published. "
                            + "No changes were saved. Please contact the faculty administrator about the required formal correction policy."
            );

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
                    e.getMessage() == null ? "Unable to save mark." : e.getMessage()
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
            NavigationUtil.navigate(marksTable, resource, title);
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

    @FXML private ComboBox<lk.ac.ruhuna.ftms.model.EligibilityOffering> offeringCombo;
    @FXML private ComboBox<lk.ac.ruhuna.ftms.model.ResultRow> enrollmentCombo;
    @FXML private ComboBox<lk.ac.ruhuna.ftms.model.Assessment> assessmentCombo;
    @FXML private ComboBox<String> markStatusCombo;
    @FXML private TableColumn<Mark,String> statusColumn;
    private void initializeSelections() {
        markStatusCombo.getItems().setAll("PRESENT","PENDING","ABSENT","MEDICAL");
        markStatusCombo.setValue("PRESENT");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("markStatus"));
        if(!"LECTURER".equals(SessionManager.getCurrentUser().getRole()))return;
        try { offeringCombo.getItems().setAll(new lk.ac.ruhuna.ftms.service.AssessmentService().getOfferings()); }
        catch(Exception error){showAlert(Alert.AlertType.ERROR,"Offerings",error.getMessage());}
        offeringCombo.valueProperty().addListener((o,old,selected)->{
            enrollmentCombo.getItems().clear();assessmentCombo.getItems().clear();clearFields();
            if(selected==null)return;
            try {
                enrollmentCombo.getItems().setAll(new lk.ac.ruhuna.ftms.service.ResultService().getBatchResults(selected.offeringId()));
                assessmentCombo.getItems().setAll(new lk.ac.ruhuna.ftms.service.AssessmentService().getAssessments(selected.offeringId()));
            }catch(Exception error){showAlert(Alert.AlertType.ERROR,"Offering",error.getMessage());}
        });
        enrollmentCombo.valueProperty().addListener((o,old,r)->{if(r!=null){enrollmentIdField.setText(""+r.enrollmentId());attemptNoField.setText(""+r.attemptNo());}});
        assessmentCombo.valueProperty().addListener((o,old,a)->{if(a!=null)assessmentIdField.setText(""+a.getAssessmentId());});
    }
    @FXML private void handleAssessments(){openAcademicScreen("assessments","/fxml/assessments.fxml","FTMS - Assessments");}
    @FXML private void handleRefresh(){loadMarks();}
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
        markStatusCombo.setValue("PRESENT");

        attemptNoField.setText("1");

        marksTable
                .getSelectionModel()
                .clearSelection();
    }

    /** Package-visible so controller tests can inspect feedback without opening a dialog. */
    void showAlert(
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
