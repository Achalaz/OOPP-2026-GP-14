package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.GpaCourseRow;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.SemesterOption;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.StudentOption;
import lk.ac.ruhuna.ftms.model.SemesterResult;
import lk.ac.ruhuna.ftms.service.GPAService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class GPAController {

    @FXML
    private ComboBox<StudentOption> studentCombo;

    @FXML
    private ComboBox<SemesterOption> semesterCombo;

    @FXML
    private TableView<GpaCourseRow> courseTable;

    @FXML
    private TableColumn<GpaCourseRow, String> courseCodeColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> courseNameColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> creditsColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> gradeColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> gradePointColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> qualityPointsColumn;

    @FXML
    private TableColumn<GpaCourseRow, String> statusColumn;

    @FXML
    private Label registeredCreditsLabel;

    @FXML
    private Label earnedCreditsLabel;

    @FXML
    private Label totalQualityPointsLabel;

    @FXML
    private Label sgpaLabel;

    @FXML
    private Label cgpaLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button calculateButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button clearButton;

    @FXML
    private Button backButton;

    private final lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity identity = lk.ac.ruhuna.ftms.util.SessionManager.requireSession();
    private final GPAService gpaService;

    private final ObservableList<GpaCourseRow> courseRows =
            FXCollections.observableArrayList();

    private final BooleanProperty busy =
            new SimpleBooleanProperty(false);

    private final ObjectProperty<SemesterResult> calculatedResult =
            new SimpleObjectProperty<>();

    public GPAController() {
        this(new GPAService());
    }

    public GPAController(GPAService gpaService) {
        this.gpaService =
                Objects.requireNonNull(gpaService);
    }

    @FXML
    public void initialize() {

        courseTable.setItems(courseRows);

        courseTable.setPlaceholder(
                new Label(
                        "Select a student and semester to view course results."
                )
        );

        setupColumns();

        // Disable controls while database work is running
        studentCombo.disableProperty()
                .bind(busy);

        semesterCombo.disableProperty()
                .bind(
                        busy.or(
                                studentCombo
                                        .valueProperty()
                                        .isNull()
                        )
                );

        courseTable.disableProperty()
                .bind(busy);

        calculateButton.disableProperty()
                .bind(
                        busy.or(
                                semesterCombo
                                        .valueProperty()
                                        .isNull()
                        )
                );

        saveButton.disableProperty()
                .bind(
                        busy.or(
                                calculatedResult.isNull()
                        )
                );

        refreshButton.disableProperty()
                .bind(busy);

        clearButton.disableProperty()
                .bind(busy);

        backButton.disableProperty()
                .bind(busy);

        // Student selection
        studentCombo.valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                handleStudentSelection(
                                        newValue
                                )
                );

        // Semester selection
        semesterCombo.valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                handleSemesterSelection(
                                        newValue
                                )
                );

        saveButton.setVisible("LECTURER".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole()));
        saveButton.setManaged(saveButton.isVisible());
        clearResultDisplay();

        loadStudents();
    }

    private void setupColumns() {

        courseCodeColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .courseCode()
                                )
                        )
        );

        courseNameColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .courseName()
                                )
                        )
        );

        creditsColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .credits()
                                )
                        )
        );

        gradeColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                valueOrDash(
                                        cell.getValue()
                                                .gradeLetter()
                                )
                        )
        );

        gradePointColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                formatNumber(
                                        cell.getValue()
                                                .gradePoint()
                                )
                        )
        );

        qualityPointsColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                qualityPointsText(
                                        cell.getValue()
                                )
                        )
        );

        statusColumn.setCellValueFactory(
                cell ->
                        new ReadOnlyStringWrapper(
                                resultState(
                                        cell.getValue()
                                )
                        )
        );
    }

    private void loadStudents() {

        calculatedResult.set(null);

        runTask(
                "Loading students...",

                gpaService::getStudents,

                students -> {

                    studentCombo.setItems(
                            FXCollections.observableArrayList(
                                    students
                            )
                    );

                    if (students.isEmpty()) {

                        statusLabel.setText(
                                "No students found."
                        );

                    } else {

                        statusLabel.setText(
                                "Select a student."
                        );
                        if ("STUDENT".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole())) {
                            studentCombo.getSelectionModel().selectFirst();
                        }
                    }
                }
        );
    }

    private void handleStudentSelection(
            StudentOption student
    ) {

        calculatedResult.set(null);

        semesterCombo.getSelectionModel()
                .clearSelection();

        semesterCombo.getItems()
                .clear();

        courseRows.clear();

        clearResultDisplay();

        if (student == null) {

            statusLabel.setText(
                    "Select a student."
            );

            return;
        }

        loadSemesters(student);
    }

    private void loadSemesters(
            StudentOption student
    ) {

        runTask(
                "Loading semesters...",

                () ->
                        gpaService
                                .getSemestersForStudent(
                                        student.studentId()
                                ),

                semesters -> {

                    semesterCombo.setItems(
                            FXCollections.observableArrayList(
                                    semesters
                            )
                    );

                    if (semesters.isEmpty()) {

                        statusLabel.setText(
                                "No semester enrollments found for "
                                        + student.registrationNo()
                                        + "."
                        );

                    } else {

                        statusLabel.setText(
                                "Select a semester."
                        );
                        if ("STUDENT".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole())) {
                            semesterCombo.getSelectionModel().selectLast();
                        }
                    }
                }
        );
    }

    private void handleSemesterSelection(
            SemesterOption semester
    ) {

        calculatedResult.set(null);

        courseRows.clear();

        clearResultDisplay();

        StudentOption student =
                studentCombo.getValue();

        if (student == null
                || semester == null) {

            return;
        }

        loadSemesterData(
                student.studentId(),
                semester.semesterId()
        );
    }

    private void loadSemesterData(
            int studentId,
            int semesterId
    ) {

        runTask(
                "Loading GPA data...",

                () -> {

                    var rows =
                            gpaService.getCourseRows(
                                    studentId,
                                    semesterId
                            );

                    SemesterResult stored =
                            gpaService.getSemesterResult(
                                    studentId,
                                    semesterId
                            );

                    return new GpaScreenData(
                            rows,
                            stored
                    );
                },

                data -> {

                    courseRows.setAll(
                            data.rows()
                    );

                    displayResult(
                            data.result()
                    );

                    if (data.rows().isEmpty()) {

                        statusLabel.setText(
                                "No course enrollments found."
                        );

                    } else if (data.result() != null) {

                        statusLabel.setText(
                                "Current published GPA loaded. Missing/unpublished results are excluded; repeat decisions remain pending."
                        );

                    } else {

                        statusLabel.setText(
                                data.rows().size()
                                        + " course result(s) loaded. "
                                        + "Click Calculate GPA."
                        );
                    }
                }
        );
    }

    @FXML
    private void handleCalculate() {

        StudentOption student =
                studentCombo.getValue();

        SemesterOption semester =
                semesterCombo.getValue();

        if (student == null
                || semester == null) {

            showValidationError(
                    "Please select a student and semester."
            );

            return;
        }

        calculatedResult.set(null);

        runTask(
                "Calculating SGPA and CGPA...",

                () ->
                        gpaService
                                .calculateSemesterGPA(
                                        student.studentId(),
                                        semester.semesterId()
                                ),

                result -> {

                    calculatedResult.set(
                            result
                    );

                    displayResult(
                            result
                    );

                    statusLabel.setText(
                            "GPA calculated. "
                                    + "Click Save / Update to store the result."
                    );
                }
        );
    }

    @FXML
    private void handleSave() {

        StudentOption student =
                studentCombo.getValue();

        SemesterOption semester =
                semesterCombo.getValue();

        if (student == null
                || semester == null) {

            showValidationError(
                    "Please select a student and semester."
            );

            return;
        }

        if (calculatedResult.get() == null) {

            showValidationError(
                    "Calculate GPA before saving."
            );

            return;
        }

        calculatedResult.set(null);

        /*
         * Recalculate before saving so stale values
         * are not stored if course results changed.
         */
        runTask(
                "Calculating and saving GPA...",

                () -> {

                    SemesterResult result =
                            gpaService
                                    .calculateSemesterGPA(
                                            student.studentId(),
                                            semester.semesterId()
                                    );

                    gpaService
                            .saveSemesterResult(
                                    result
                            );

                    return gpaService.getSemesterResult(student.studentId(),semester.semesterId());
                },

                result -> {

                    displayResult(
                            result
                    );

                    statusLabel.setText(
                            "Semester GPA saved successfully."
                    );
                }
        );
    }

    @FXML
    private void handleRefresh() {

        StudentOption student =
                studentCombo.getValue();

        SemesterOption semester =
                semesterCombo.getValue();

        calculatedResult.set(null);

        if (student == null) {

            loadStudents();

            return;
        }

        if (semester == null) {

            loadSemesters(
                    student
            );

            return;
        }

        loadSemesterData(
                student.studentId(),
                semester.semesterId()
        );
    }

    @FXML
    private void handleClear() {

        calculatedResult.set(null);

        studentCombo.getSelectionModel()
                .clearSelection();

        semesterCombo.getSelectionModel()
                .clearSelection();

        semesterCombo.getItems()
                .clear();

        courseRows.clear();

        clearResultDisplay();

        statusLabel.setText(
                "Select a student."
        );
    }

    @FXML
    private void handleBack() {

        try {

            NavigationUtil.navigate(
                    courseTable,
                    NavigationUtil.dashboardResource(),
                    "FTMS - Results Management"
            );

        } catch (IOException error) {

            showError(
                    "Unable to open Results Management.",
                    error
            );
        }
    }

    @FXML private void handleReports() throws IOException { NavigationUtil.navigate(courseTable,"/fxml/reports.fxml","FTMS - Reports"); }
    private void displayResult(
            SemesterResult result
    ) {

        if (result == null) {

            clearResultDisplay();

            return;
        }

        registeredCreditsLabel.setText(
                formatNumber(
                        result.getTotalRegisteredCredits()
                )
        );

        earnedCreditsLabel.setText(
                formatNumber(
                        result.getTotalEarnedCredits()
                )
        );

        totalQualityPointsLabel.setText(
                formatNumber(
                        result.getTotalQualityPoints()
                )
        );

        sgpaLabel.setText(
                formatNumber(
                        result.getSgpa()
                )
        );

        cgpaLabel.setText(
                formatNumber(
                        result.getCgpa()
                )
        );
    }

    private void clearResultDisplay() {

        registeredCreditsLabel.setText("--");
        earnedCreditsLabel.setText("--");
        totalQualityPointsLabel.setText("--");
        sgpaLabel.setText("--");
        cgpaLabel.setText("--");
    }

    /*
     * Table-display helper only.
     * Actual GPA totals are calculated in GPAService.
     */
    private String qualityPointsText(
            GpaCourseRow row
    ) {

        return formatNumber(GPAService.qualityPoints(row));
    }

    private boolean isCompletedResult(
            String resultStatus
    ) {

        if (resultStatus == null) {
            return false;
        }

        return resultStatus.equalsIgnoreCase(
                "PASS"
        ) || resultStatus.equalsIgnoreCase(
                "FAIL"
        );
    }

    private String resultState(
            GpaCourseRow row
    ) {

        if (row == null) {
            return "--";
        }

        if (row.resultStatus() == null) {
            return "No Result";
        }

        if (!row.published()) {
            return row.resultStatus()
                    + " - Draft";
        }

        return row.resultStatus()
                + " - Published";
    }

    private String valueOrDash(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return "--";
        }

        return value;
    }

    private String formatNumber(
            Double value
    ) {

        if (value == null) {
            return "--";
        }

        return String.format(
                Locale.ROOT,
                "%.2f",
                value
        );
    }

    private String formatNumber(
            double value
    ) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                value
        );
    }

    private <T> void runTask(
            String message,
            Callable<T> work,
            Consumer<T> onSuccess
    ) {

        busy.set(true);

        statusLabel.setText(
                message
        );

        Task<T> task =
                new Task<>() {

                    @Override
                    protected T call()
                            throws Exception {

                        lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);
                        return work.call();
                    }
                };

        task.setOnSucceeded(
                event -> {

                    busy.set(false);

                    if (!lk.ac.ruhuna.ftms.util.SessionManager.isCurrentSession(identity)) { courseRows.clear(); clearResultDisplay(); return; }
                    onSuccess.accept(
                            task.getValue()
                    );
                }
        );

        task.setOnFailed(
                event -> {

                    busy.set(false);

                    calculatedResult.set(null);

                    courseRows.clear(); clearResultDisplay();
                    Throwable error =
                            task.getException();

                    if (error
                            instanceof IllegalArgumentException) {

                        showValidationError(
                                error.getMessage()
                        );

                    } else {

                        showError(
                                "Unable to complete the GPA action. "
                                        + "Check the database connection and retry.",
                                error
                        );
                    }
                }
        );

        Thread thread =
                new Thread(
                        task,
                        "ftms-gpa"
                );

        thread.setDaemon(true);
        thread.start();
    }

    private void showValidationError(
            String message
    ) {

        statusLabel.setText(
                message
        );

        Alert alert =
                new Alert(
                        Alert.AlertType.WARNING
                );

        alert.setTitle(
                "GPA Validation"
        );

        alert.setHeaderText(null);

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    private void showError(
            String message,
            Throwable error
    ) {

        statusLabel.setText(
                message
        );

        if (error != null) {
            error.printStackTrace();
        }

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "GPA Error"
        );

        alert.setHeaderText(null);

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    private record GpaScreenData(
            java.util.List<GpaCourseRow> rows,
            SemesterResult result
    ) {
    }
}
