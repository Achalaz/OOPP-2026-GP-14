package lk.ac.ruhuna.ftms.controller;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO.LecturerOption;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.service.LecturerAssignmentService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

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

    @FXML private TableView<LecturerCourseAssignment> assignedLecturerTable;
    @FXML private TableColumn<LecturerCourseAssignment, String> lecturerNameColumn;
    @FXML private TableColumn<LecturerCourseAssignment, String> lecturerStaffColumn;
    @FXML private TableColumn<LecturerCourseAssignment, AssignmentType> assignmentTypeColumn;
    @FXML private TableColumn<LecturerCourseAssignment, String> assignmentCourseColumn;
    @FXML private TableColumn<LecturerCourseAssignment, String> assignmentGroupColumn;
    @FXML private TableColumn<LecturerCourseAssignment, String> assignmentSemesterColumn;
    @FXML private ComboBox<LecturerOption> lecturerComboBox;
    @FXML private ComboBox<AssignmentType> assignmentTypeComboBox;
    @FXML private Label assignmentOfferingLabel;
    @FXML private Label assignmentStatusLabel;
    @FXML private javafx.scene.layout.VBox assignmentEditor;
    @FXML private Button updateAssignmentButton;
    @FXML private Button removeAssignmentButton;
    @FXML private Button refreshAssignmentsButton;

    private CourseOfferingService offeringService;
    private LecturerAssignmentService assignmentService;
    private int selectedOfferingId = 0;

    @FXML
    private void initialize() {
        // Guard direct FXMLLoader access as well as navigation through the dashboard.
        if (!CourseService.isCurrentUserAdmin()) {
            throw new SecurityException("Active administrator access is required to manage course offerings.");
        }
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

        lecturerNameColumn.setCellValueFactory(new PropertyValueFactory<>("lecturerName"));
        lecturerStaffColumn.setCellValueFactory(new PropertyValueFactory<>("staffNo"));
        assignmentTypeColumn.setCellValueFactory(new PropertyValueFactory<>("assignmentType"));
        assignmentCourseColumn.setCellValueFactory(new PropertyValueFactory<>("courseCode"));
        assignmentGroupColumn.setCellValueFactory(new PropertyValueFactory<>("offeringGroup"));
        assignmentSemesterColumn.setCellValueFactory(new PropertyValueFactory<>("semester"));
        assignmentTypeComboBox.setItems(FXCollections.observableArrayList(AssignmentType.values()));
        assignmentTypeComboBox.setValue(AssignmentType.PRIMARY);
        updateAssignmentButton.disableProperty().bind(assignedLecturerTable.getSelectionModel().selectedItemProperty().isNull());
        removeAssignmentButton.disableProperty().bind(assignedLecturerTable.getSelectionModel().selectedItemProperty().isNull());
        refreshAssignmentsButton.disableProperty().bind(offeringTable.getSelectionModel().selectedItemProperty().isNull());
        assignmentEditor.setDisable(true);
        assignedLecturerTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (selected != null) {
                assignmentTypeComboBox.setValue(selected.getAssignmentType());
                lecturerComboBox.setValue(lecturerComboBox.getItems().stream()
                        .filter(l -> l.lecturerId() == selected.getLecturerId()).findFirst().orElse(null));
            }
        });

        offeringTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {
                    if (selected != null) {
                        populateForm(selected);
                    }
                    selectAssignmentOffering(selected);
                });
    }

    // Called once by NavigationUtil after FXML loading, before displaying the screen.
    public void setCourseOfferingService(
            CourseOfferingService service) {

        if (offeringService != null) {
            throw new IllegalStateException("Course Offering Service is already initialized.");
        }
        this.offeringService = Objects.requireNonNull(service);
        loadDropdowns();
        refreshTable();
    }

    public void setLecturerAssignmentService(LecturerAssignmentService service) {
        if (assignmentService != null) throw new IllegalStateException("Lecturer Assignment Service is already initialized.");
        assignmentService = Objects.requireNonNull(service);
        selectAssignmentOffering(offeringTable.getSelectionModel().getSelectedItem());
        loadEligibleLecturers();
    }

    private void selectAssignmentOffering(CourseOffering offering) {
        assignedLecturerTable.getItems().clear();
        lecturerComboBox.setValue(null);
        assignmentTypeComboBox.setValue(AssignmentType.PRIMARY);
        assignmentStatusLabel.setText("");
        assignmentOfferingLabel.setText(offering == null ? "Select a course offering above to manage its lecturers."
                : offering.getCourseCode() + " / Group " + offering.getOfferingGroup()
                  + " / " + offering.getSemesterName() + " / " + offering.getAcademicYearName());
        assignmentEditor.setDisable(offering == null || assignmentService == null || "CANCELLED".equals(offering.getStatus()));
        if (offering != null && assignmentService != null) {
            boolean loaded = reloadAssignments(offering.getOfferingId());
            if (loaded && "CANCELLED".equals(offering.getStatus())) assignmentMessage("Cancelled offering: assignments are retained for reference.", false);
        }
    }

    private void loadEligibleLecturers() {
        if (assignmentService == null) return;
        try {
            lecturerComboBox.setItems(FXCollections.observableArrayList(assignmentService.getEligibleLecturers()));
        } catch (SQLException | RuntimeException error) { assignmentError(error); }
    }

    private boolean reloadAssignments(int offeringId) {
        // Clear first so a failed request never leaves another offering's assignments visible.
        assignedLecturerTable.getItems().clear();
        try {
            assignedLecturerTable.setItems(FXCollections.observableArrayList(assignmentService.getAssignmentsByOffering(offeringId)));
            return true;
        } catch (SQLException | RuntimeException error) { assignmentError(error); return false; }
    }

    private CourseOffering requireAssignmentOffering() {
        if (assignmentService == null) throw new IllegalStateException("Lecturer assignments are not ready. Reopen Course Offerings.");
        CourseOffering selected = offeringTable.getSelectionModel().getSelectedItem();
        if (selected == null) throw new IllegalArgumentException("Select a course offering first.");
        return selected;
    }

    @FXML
    private void handleAssignLecturer() {
        try {
            CourseOffering offering = requireAssignmentOffering();
            LecturerOption lecturer = lecturerComboBox.getValue();
            if (lecturer == null) throw new IllegalArgumentException("Select an active lecturer.");
            assignmentService.assignLecturer(offering.getOfferingId(), lecturer.lecturerId(), assignmentTypeComboBox.getValue());
            boolean reloaded = reloadAssignments(offering.getOfferingId());
            lecturerComboBox.setValue(null);
            if (reloaded) assignmentMessage("Lecturer assigned successfully.", false);
        } catch (SQLException | RuntimeException error) { assignmentError(error); }
    }

    @FXML
    private void handleUpdateAssignmentType() {
        changeAssignment(false);
    }

    @FXML
    private void handleRemoveAssignment() {
        changeAssignment(true);
    }

    private void changeAssignment(boolean remove) {
        try {
            CourseOffering offering = requireAssignmentOffering();
            LecturerCourseAssignment assignment = assignedLecturerTable.getSelectionModel().getSelectedItem();
            if (assignment == null) throw new IllegalArgumentException("Select an assigned lecturer.");
            AssignmentType type = assignmentTypeComboBox.getValue();
            if (!remove && type == null) throw new IllegalArgumentException("Select an assignment type.");
            if (!remove && type == assignment.getAssignmentType()) {
                assignmentMessage("This assignment already has the selected type.", false);
                return;
            }
            String blocker = assignmentService.getAssignmentChangeBlocker(offering.getOfferingId(), assignment.getAssignmentId());
            if (blocker != null) {
                assignmentMessage(blocker, true);
                return;
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Lecturer Assignment");
            confirmation.setHeaderText(remove ? "Remove this lecturer's assignment?" : "Change this assignment type?");
            confirmation.setContentText(assignment.getLecturerName() + " / " + offering.getCourseCode()
                    + " / Group " + offering.getOfferingGroup() + "\n"
                    + (remove ? "This removes the lecturer's access to this offering. Academic records will be preserved."
                              : "Change " + assignment.getAssignmentType() + " to " + type + ". Academic records will be preserved."));
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            if (remove) assignmentService.removeAssignment(offering.getOfferingId(), assignment.getAssignmentId());
            else assignmentService.updateAssignmentType(offering.getOfferingId(), assignment.getAssignmentId(), type);
            boolean reloaded = reloadAssignments(offering.getOfferingId());
            lecturerComboBox.setValue(null);
            if (reloaded) assignmentMessage(remove ? "Assignment removed. Academic records were preserved." : "Assignment type updated successfully.", false);
        } catch (SQLException | RuntimeException error) { assignmentError(error); }
    }

    @FXML
    private void handleRefreshAssignments() {
        try {
            CourseOffering offering = requireAssignmentOffering();
            assignmentStatusLabel.setText("");
            lecturerComboBox.setValue(null);
            loadEligibleLecturers();
            reloadAssignments(offering.getOfferingId());
        } catch (RuntimeException error) { assignmentError(error); }
    }

    private void assignmentError(Exception error) {
        String message = error.getMessage();
        if (error instanceof SQLException sql) {
            message = switch (sql.getErrorCode()) {
                case 1062 -> "This lecturer is already assigned. Select the existing assignment to change its type.";
                case 1451 -> "This assignment is linked to academic records and must be retained.";
                case 1452 -> "The offering or lecturer is no longer available. Refresh and try again.";
                case 1205, 1213 -> "The assignment changed in another operation. Refresh and try again.";
                default -> "Could not complete the assignment operation. Check the database connection and refresh.";
            };
        }
        assignmentMessage(message == null ? "Could not complete the assignment operation." : message, true);
    }

    private void assignmentMessage(String message, boolean error) {
        assignmentStatusLabel.setText(message);
        assignmentStatusLabel.setStyle(error ? "-fx-text-fill: #AD3030;" : "-fx-text-fill: #2865A7;");
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
            showOperationError("Could not load courses or semesters", e);
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
            showOperationError("Could not save offering", e);
        }
    }

    private void populateForm(CourseOffering offering) {
        selectedOfferingId = offering.getOfferingId();

        // An existing offering may refer to a course that has since been deactivated.
        // Keep its actual course identity instead of retaining a previous selection.
        courseCombo.setValue(courseCombo.getItems().stream()
                .filter(c -> c.courseId() == offering.getCourseId())
                .findFirst()
                .orElseGet(() -> new CourseOfferingDAO.CourseOption(
                        offering.getCourseId(), offering.getCourseCode(),
                        offering.getCourseName(), offering.getCredits())));

        semesterCombo.setValue(semesterCombo.getItems().stream()
                .filter(s -> s.semesterId()
                        == offering.getSemesterId())
                .findFirst()
                .orElse(null));

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
            showOperationError("Could not delete offering", e);
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
            clearForm();
        } catch (SQLException | RuntimeException e) {
            showOperationError("Search failed", e);
        }
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        loadDropdowns();
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
            clearForm();
        } catch (SQLException | RuntimeException e) {
            showOperationError("Could not load offerings", e);
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    @FXML
    private void handleBackToDashboard() {
        try {
            NavigationUtil.navigate(offeringTable, "/fxml/admin_dashboard.fxml", "FTMS - Admin Dashboard");
        } catch (IOException | RuntimeException e) {
            showError("Unable to open the Admin Dashboard. Please check your session and try again.");
        }
    }

    private void clearForm() {
        selectedOfferingId = 0;

        offeringTable.getSelectionModel().clearSelection();

        courseCombo.setValue(null);
        semesterCombo.setValue(null);
        groupField.clear();
        statusCombo.setValue("PLANNED");
    }

    private void showOperationError(String operation, Exception error) {
        String message = error.getMessage();
        if (error instanceof SQLException sql) {
            message = switch (sql.getErrorCode()) {
                case 1062 -> "An offering for this course, semester and group already exists.";
                case 1451 -> "This offering is linked to academic records and cannot be deleted. "
                        + "You can change its status to CANCELLED instead.";
                case 1452 -> "The selected course or semester no longer exists. Refresh and select again.";
                case 1264, 1265, 1406, 3819 -> "Check the offering group and select a valid offering status.";
                default -> "Check the database connection and try again.";
            };
        }
        showError(operation + ": " + (message == null ? "An unexpected error occurred." : message));
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
