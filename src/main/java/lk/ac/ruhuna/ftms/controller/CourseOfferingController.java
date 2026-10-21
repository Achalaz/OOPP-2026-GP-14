package lk.ac.ruhuna.ftms.controller;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;

import java.sql.SQLException;
import java.util.List;

public class CourseOfferingController {

    @FXML private TableView<CourseOffering> offeringTable;

    @FXML private TableColumn<CourseOffering, String> codeColumn;
    @FXML private TableColumn<CourseOffering, String> nameColumn;
    @FXML private TableColumn<CourseOffering, Double> creditsColumn;
    @FXML private TableColumn<CourseOffering, String> semesterColumn;
    @FXML private TableColumn<CourseOffering, String> yearColumn;
    @FXML private TableColumn<CourseOffering, String> groupColumn;
    @FXML private TableColumn<CourseOffering, String> statusColumn;

    @FXML private ComboBox<CourseOfferingDAO.CourseOption> courseCombo;
    @FXML private ComboBox<CourseOfferingDAO.SemesterOption> semesterCombo;
    @FXML private ComboBox<String> statusCombo;

    @FXML private TextField groupField;
    @FXML private TextField searchField;

    private CourseOfferingService offeringService;
    private int selectedOfferingId = 0;

    @FXML
    private void initialize() {
        codeColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseCode"));
        nameColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseName"));
        creditsColumn.setCellValueFactory(
                new PropertyValueFactory<>("credits"));
        semesterColumn.setCellValueFactory(
                new PropertyValueFactory<>("semesterName"));
        yearColumn.setCellValueFactory(
                new PropertyValueFactory<>("academicYearName"));
        groupColumn.setCellValueFactory(
                new PropertyValueFactory<>("offeringGroup"));
        statusColumn.setCellValueFactory(
                new PropertyValueFactory<>("status"));

        statusCombo.setItems(FXCollections.observableArrayList(
                "PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"
        ));
        statusCombo.setValue("PLANNED");

        offeringTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {
                    if (selected != null) {
                        populateForm(selected);
                    }
                });
    }

    // Called by the dashboard after loading this FXML.
    public void setCourseOfferingService(
            CourseOfferingService service) {

        this.offeringService = service;
        loadDropdowns();
        refreshTable();
    }

    private void loadDropdowns() {
        if (offeringService == null) {
            return;
        }

        try {
            courseCombo.setItems(
                    FXCollections.observableArrayList(
                            offeringService.getActiveCourses()
                    )
            );

            semesterCombo.setItems(
                    FXCollections.observableArrayList(
                            offeringService.getSemesters()
                    )
            );
        } catch (SQLException | RuntimeException e) {
            showError("Could not load courses or semesters: "
                    + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (offeringService == null) {
            showError("Course Offering Service is not initialized.");
            return;
        }

        if (courseCombo.getValue() == null
                || semesterCombo.getValue() == null
                || groupField.getText() == null
                || groupField.getText().isBlank()
                || statusCombo.getValue() == null) {

            showError(
                    "Select a course and semester, enter a group, "
                            + "and select a status."
            );
            return;
        }

        CourseOffering offering = new CourseOffering();

        offering.setOfferingId(selectedOfferingId);
        offering.setCourseId(
                courseCombo.getValue().courseId());
        offering.setSemesterId(
                semesterCombo.getValue().semesterId());
        offering.setOfferingGroup(groupField.getText().trim());
        offering.setStatus(statusCombo.getValue());

        try {
            boolean saved = offeringService.saveOffering(offering);

            if (saved) {
                showInfo(selectedOfferingId == 0
                        ? "Course offering created successfully."
                        : "Course offering updated successfully.");

                refreshTable();
                clearForm();
            } else {
                showError("The course offering was not saved.");
            }
        } catch (SQLException | RuntimeException e) {
            showError("Could not save offering: " + e.getMessage());
        }
    }

    private void populateForm(CourseOffering offering) {
        selectedOfferingId = offering.getOfferingId();

        courseCombo.getItems().stream()
                .filter(c -> c.courseId() == offering.getCourseId())
                .findFirst()
                .ifPresent(courseCombo::setValue);

        semesterCombo.getItems().stream()
                .filter(s -> s.semesterId()
                        == offering.getSemesterId())
                .findFirst()
                .ifPresent(semesterCombo::setValue);

        groupField.setText(offering.getOfferingGroup());
        statusCombo.setValue(offering.getStatus());
    }

    @FXML
    private void handleDelete() {
        if (offeringService == null) {
            showError("Course Offering Service is not initialized.");
            return;
        }

        CourseOffering selected =
                offeringTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showError("Please select an offering to delete.");
            return;
        }

        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION
        );
        confirmation.setTitle("Delete Course Offering");
        confirmation.setHeaderText("Delete selected offering?");
        confirmation.setContentText(
                selected.getCourseCode() + " - "
                        + selected.getOfferingGroup()
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL)
                != ButtonType.OK) {
            return;
        }

        try {
            if (offeringService.deleteOffering(
                    selected.getOfferingId())) {

                showInfo("Course offering deleted successfully.");
                refreshTable();
                clearForm();
            } else {
                showError("No offering was deleted.");
            }
        } catch (SQLException | RuntimeException e) {
            showError("Could not delete offering: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        if (offeringService == null) {
            return;
        }

        try {
            String keyword = searchField.getText();

            List<CourseOffering> results =
                    offeringService.searchOfferings(keyword);

            offeringTable.setItems(
                    FXCollections.observableArrayList(results)
            );
        } catch (SQLException | RuntimeException e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshTable();
    }

    private void refreshTable() {
        if (offeringService == null) {
            return;
        }

        try {
            offeringTable.setItems(
                    FXCollections.observableArrayList(
                            offeringService.getAllOfferings()
                    )
            );
        } catch (SQLException | RuntimeException e) {
            showError("Could not load offerings: " + e.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    private void clearForm() {
        selectedOfferingId = 0;

        offeringTable.getSelectionModel().clearSelection();

        courseCombo.setValue(null);
        semesterCombo.setValue(null);
        groupField.clear();
        statusCombo.setValue("PLANNED");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Course Offering Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Course Offering Management");
        alert.setHeaderText("Operation failed");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
