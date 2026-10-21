package lk.ac.ruhuna.ftms.controller;

import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;

public class CourseController {

    @FXML private TextField searchField;
    @FXML private TextField courseCodeField;
    @FXML private TextField courseNameField;
    @FXML private TextField creditsField;
    @FXML private TextField departmentIdField;

    @FXML private ComboBox<String> courseTypeComboBox;
    @FXML private CheckBox activeCheckBox;

    @FXML private TableView<Course> courseTable;
    @FXML private TableColumn<Course, Integer> idColumn;
    @FXML private TableColumn<Course, String> codeColumn;
    @FXML private TableColumn<Course, String> nameColumn;
    @FXML private TableColumn<Course, BigDecimal> creditsColumn;
    @FXML private TableColumn<Course, Integer> departmentColumn;
    @FXML private TableColumn<Course, String> typeColumn;
    @FXML private TableColumn<Course, Boolean> activeColumn;

    private final ObservableList<Course> courseList =
            FXCollections.observableArrayList();

    private CourseService courseService;

    @FXML
    public void initialize() {
        requireAdminAccess();
        courseTypeComboBox.getItems().setAll(
                "CORE", "ELECTIVE", "OPTIONAL"
        );

        courseTypeComboBox.setValue("CORE");
        activeCheckBox.setSelected(true);

        idColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseId"));

        codeColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseCode"));

        nameColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseName"));

        creditsColumn.setCellValueFactory(
                new PropertyValueFactory<>("credits"));

        departmentColumn.setCellValueFactory(
                new PropertyValueFactory<>("departmentId"));

        typeColumn.setCellValueFactory(
                new PropertyValueFactory<>("courseType"));

        activeColumn.setCellValueFactory(
                new PropertyValueFactory<>("active"));

        courseTable.setItems(courseList);

        courseTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldCourse, selectedCourse) -> {
                    if (selectedCourse != null) {
                        fillForm(selectedCourse);
                    }
                });
    }

    // Called by the navigation code after loading courses.fxml.
    public void setCourseService(CourseService courseService) {
        requireAdminAccess();
        this.courseService = Objects.requireNonNull(courseService, "Course service is required.");
        loadCourses();
    }

    private void requireAdminAccess() {
        if (!CourseService.isCurrentUserAdmin()) {
            throw new SecurityException("Active administrator access is required to manage courses.");
        }
    }

    private CourseService requireCourseService() {
        requireAdminAccess();
        if (courseService == null) {
            throw new IllegalStateException("Course management is not ready. Return to the dashboard and open Courses again.");
        }
        return courseService;
    }

    @FXML
    public void handleBackToDashboard() {
        try {
            requireAdminAccess();
            NavigationUtil.navigate(courseTable, "/fxml/admin_dashboard.fxml", "FTMS - Admin Dashboard");
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    public void handleAdd() {
        try {
            requireCourseService();
            Course course = readForm();
            courseService.createCourse(course);

            showInfo("Course added successfully.");
            clearForm();
            loadCourses();

        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    public void handleUpdate() {
        Course selected = courseTable.getSelectionModel()
                .getSelectedItem();

        if (selected == null) {
            showWarning("Select a course to update.");
            return;
        }

        try {
            requireCourseService();
            Course course = readForm();
            course.setCourseId(selected.getCourseId());

            if (courseService.updateCourse(course)) {
                showInfo("Course updated successfully.");
                clearForm();
                loadCourses();
            } else {
                showWarning("Course was not found.");
            }

        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    public void handleDelete() {
        try {
            requireCourseService();
        } catch (Exception e) {
            showError(e);
            return;
        }
        Course selected = courseTable.getSelectionModel()
                .getSelectedItem();

        if (selected == null) {
            showWarning("Select a course to delete.");
            return;
        }

        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION);

        confirmation.setTitle("Delete Course");
        confirmation.setHeaderText("Delete " + selected.getCourseCode() + "?");
        confirmation.setContentText(
                "This action may be blocked if other records depend on this course.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    if (requireCourseService().deleteCourse(
                            selected.getCourseId())) {

                        showInfo("Course deleted successfully.");
                        clearForm();
                        loadCourses();

                    } else {
                        showWarning("Course was not found.");
                    }

                } catch (Exception e) {
                    showError(e);
                }
            }
        });
    }

    @FXML
    public void handleSearch() {
        try {
            courseList.setAll(
                    requireCourseService().searchCourses(searchField.getText()));
            clearForm();

        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    public void handleRefresh() {
        if (searchField != null) {
            searchField.clear();
        }

        loadCourses();
    }

    @FXML
    public void handleClear() {
        clearForm();
    }

    private Course readForm() {
        Course course = new Course();

        course.setCourseCode(courseCodeField.getText());
        course.setCourseName(courseNameField.getText());

        try {
            course.setCredits(new BigDecimal(creditsField.getText().trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter credits as a number, for example 3.0.");
        }

        try {
            course.setDepartmentId(Integer.parseInt(departmentIdField.getText().trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter an existing department ID as a whole number.");
        }

        course.setCourseType(courseTypeComboBox.getValue());
        course.setActive(activeCheckBox.isSelected());

        return course;
    }

    private void fillForm(Course course) {
        courseCodeField.setText(course.getCourseCode());
        courseNameField.setText(course.getCourseName());

        creditsField.setText(
                course.getCredits() == null
                        ? ""
                        : course.getCredits().toPlainString());

        departmentIdField.setText(
                String.valueOf(course.getDepartmentId()));

        courseTypeComboBox.setValue(course.getCourseType());
        activeCheckBox.setSelected(course.isActive());
    }

    private void clearForm() {
        courseTable.getSelectionModel().clearSelection();

        courseCodeField.clear();
        courseNameField.clear();
        creditsField.clear();
        departmentIdField.clear();

        courseTypeComboBox.setValue("CORE");
        activeCheckBox.setSelected(true);
    }

    private void loadCourses() {
        try {
            courseList.setAll(requireCourseService().getAllCourses());
            clearForm();

        } catch (SQLException | RuntimeException e) {
            showError(e);
        }
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message)
                .showAndWait();
    }

    private void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message)
                .showAndWait();
    }

    private void showError(Exception e) {
        String message = e.getMessage();

        if (e instanceof SQLException sql) {
            message = switch (sql.getErrorCode()) {
                case 1062 -> "That course code already exists. Use a unique course code.";
                case 1451 -> "This course is used by academic records and cannot be deleted. You can mark it inactive instead.";
                case 1452 -> "The department does not exist. Enter an existing department ID.";
                case 1264, 1265, 1406, 3819 -> "Check the course details: code and name length, credits, department, and course type.";
                default -> "Unable to complete the course operation. Check the database connection and try again.";
            };
        }

        if (message == null || message.isBlank()) {
            message = "An unexpected error occurred.";
        }

        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}
