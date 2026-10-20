
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import lk.ac.ruhuna.ftms.dao.StudentAttendanceDAO.CourseAttendance;
import lk.ac.ruhuna.ftms.dao.StudentAttendanceDAO.SessionAttendance;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.StudentAttendanceService;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class StudentAttendanceController {

    @FXML private BorderPane studentAttendanceRoot;

    @FXML private Label studentNameLabel;
    @FXML private Label courseDetailsLabel;
    @FXML private Label statusLabel;

    @FXML private Button refreshButton;
    @FXML private Button backButton;

    // Course-wise attendance table

    @FXML private TableView<CourseAttendance> courseTable;

    @FXML private TableColumn<CourseAttendance, String>
            courseCodeColumn;

    @FXML private TableColumn<CourseAttendance, String>
            courseNameColumn;

    @FXML private TableColumn<CourseAttendance, String>
            theoryColumn;

    @FXML private TableColumn<CourseAttendance, String>
            practicalColumn;

    @FXML private TableColumn<CourseAttendance, String>
            combinedColumn;

    @FXML private TableColumn<CourseAttendance, String>
            absentColumn;

    @FXML private TableColumn<CourseAttendance, String>
            lateColumn;

    @FXML private TableColumn<CourseAttendance, String>
            excusedColumn;

    @FXML private TableColumn<CourseAttendance, String>
            medicalColumn;

    @FXML private TableColumn<CourseAttendance, String>
            unmarkedColumn;

    // Individual session attendance table

    @FXML private TableView<SessionAttendance> sessionTable;

    @FXML private TableColumn<SessionAttendance, String>
            sessionDateColumn;

    @FXML private TableColumn<SessionAttendance, String>
            sessionTypeColumn;

    @FXML private TableColumn<SessionAttendance, String>
            sessionTimeColumn;

    @FXML private TableColumn<SessionAttendance, String>
            sessionTopicColumn;

    @FXML private TableColumn<SessionAttendance, String>
            sessionStatusColumn;

    // Observable table data

    private final ObservableList<CourseAttendance> courses =
            FXCollections.observableArrayList();

    private final ObservableList<SessionAttendance> sessions =
            FXCollections.observableArrayList();

    private final SimpleBooleanProperty busy =
            new SimpleBooleanProperty(false);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private SessionIdentity loginIdentity;

    private StudentAttendanceService attendanceService;

    // Initialize attendance screen

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        User student = requireStudent();

        attendanceService = new StudentAttendanceService();

        studentNameLabel.setText(
                "My Attendance - " + student.getFullName()
        );

        // Course table columns

        courseCodeColumn.setCellValueFactory(
                cell -> text(cell.getValue().courseCode())
        );

        courseNameColumn.setCellValueFactory(
                cell -> text(cell.getValue().courseName())
        );

        theoryColumn.setCellValueFactory(
                cell -> text(progress(
                        cell.getValue().theoryPresent(),
                        cell.getValue().theorySessions(),
                        cell.getValue().getTheoryPercentage()
                ))
        );

        practicalColumn.setCellValueFactory(
                cell -> text(progress(
                        cell.getValue().practicalPresent(),
                        cell.getValue().practicalSessions(),
                        cell.getValue().getPracticalPercentage()
                ))
        );

        combinedColumn.setCellValueFactory(
                cell -> text(progress(
                        cell.getValue().getTotalPresent(),
                        cell.getValue().getTotalSessions(),
                        cell.getValue().getCombinedPercentage()
                ))
        );

        absentColumn.setCellValueFactory(
                cell -> text(String.valueOf(
                        cell.getValue().absentCount()
                ))
        );

        lateColumn.setCellValueFactory(
                cell -> text(String.valueOf(
                        cell.getValue().lateCount()
                ))
        );

        excusedColumn.setCellValueFactory(
                cell -> text(String.valueOf(
                        cell.getValue().excusedCount()
                ))
        );

        medicalColumn.setCellValueFactory(
                cell -> text(String.valueOf(
                        cell.getValue().medicalCount()
                ))
        );

        unmarkedColumn.setCellValueFactory(
                cell -> text(String.valueOf(
                        cell.getValue().unmarkedCount()
                ))
        );

        courseTable.setItems(courses);

        courseTable.setPlaceholder(
                new Label("No enrolled courses found.")
        );

        // Session table columns

        sessionDateColumn.setCellValueFactory(
                cell -> text(
                        cell.getValue().sessionDate() == null
                                ? "-"
                                : cell.getValue()
                                .sessionDate()
                                .format(DATE_FORMAT)
                )
        );

        sessionTypeColumn.setCellValueFactory(
                cell -> text(cell.getValue().sessionType())
        );

        sessionTimeColumn.setCellValueFactory(
                cell -> text(time(cell.getValue()))
        );

        sessionTopicColumn.setCellValueFactory(
                cell -> text(cell.getValue().topic())
        );

        sessionStatusColumn.setCellValueFactory(
                cell -> text(cell.getValue().attendanceStatus())
        );

        sessionTable.setItems(sessions);

        sessionTable.setPlaceholder(
                new Label("Select a course to see sessions.")
        );

        // Disable controls while data loads

        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);
        courseTable.disableProperty().bind(busy);
        sessionTable.disableProperty().bind(busy);

        // Load sessions when selecting a course

        courseTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, previous, selected) -> {

                    sessions.clear();

                    if (selected == null) {

                        courseDetailsLabel.setText(
                                "Select a course to view attendance."
                        );

                    } else if (!busy.get()) {

                        loadSessions(selected.enrollmentId());
                    }
                });

        courseDetailsLabel.setText(
                "Select a course to view attendance."
        );

        loadCourses();
    }

    // Refresh attendance

    @FXML
    private void handleRefresh() {

        requireStudent();

        loadCourses();
    }

    // Back to ORIGINAL Student Dashboard

    @FXML
    private void handleBack() {

        try {

            requireStudent();

            URL location = getClass().getResource(
                    "/fxml/student_dashboard.fxml"
            );

            if (location == null) {

                throw new IOException(
                        "Original student dashboard FXML not found."
                );
            }

            Parent dashboard = FXMLLoader.load(location);

            studentAttendanceRoot.getScene().setRoot(dashboard);

            if (studentAttendanceRoot.getScene().getWindow()
                    instanceof Stage stage) {

                stage.setTitle("FTMS - Student Dashboard");
            }

        } catch (IOException | RuntimeException ex) {

            showError(
                    "Unable to return to the Student Dashboard.",
                    ex
            );
        }
    }

    // Verify logged-in Student

    private User requireStudent() {

        SessionManager.requireSession(loginIdentity);

        User student = SessionManager.getCurrentUser();

        if (student == null
                || !student.isActive()
                || !"STUDENT".equals(student.getRole())
                || student.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "A valid student login is required."
            );
        }

        return student;
    }

    // Load all enrolled courses

    private void loadCourses() {

        runTask(
                "Loading your attendance...",

                attendanceService::getMyCourseAttendance,

                result -> {

                    courseTable.getSelectionModel()
                            .clearSelection();

                    sessions.clear();

                    courses.setAll(result);

                    if (result.isEmpty()) {

                        courseDetailsLabel.setText(
                                "No course attendance to display."
                        );

                        statusLabel.setText(
                                "No enrollments found for this student."
                        );

                    } else {

                        statusLabel.setText(
                                result.size() + " courses loaded."
                        );

                        courseTable.getSelectionModel()
                                .selectFirst();
                    }
                }
        );
    }

    // Load session records of selected course

    private void loadSessions(int enrollmentId) {

        runTask(
                "Loading sessions...",

                () -> attendanceService
                        .getMySessionAttendance(enrollmentId),

                result -> {

                    CourseAttendance selected =
                            courseTable.getSelectionModel()
                                    .getSelectedItem();

                    if (selected == null
                            || selected.enrollmentId() != enrollmentId) {

                        return;
                    }

                    sessions.setAll(result);

                    courseDetailsLabel.setText(
                            selected.courseCode()
                                    + " - "
                                    + selected.courseName()

                                    + " | Combined: "
                                    + percent(selected.getCombinedPercentage())

                                    + " | Medical: "
                                    + selected.medicalCount()

                                    + " | Unmarked: "
                                    + selected.unmarkedCount()
                    );

                    statusLabel.setText(
                            result.size() + " sessions loaded."
                    );
                }
        );
    }

    // Execute database operations outside JavaFX UI thread

    private <T> void runTask(
            String message,
            Callable<T> databaseWork,
            Consumer<T> onSuccess) {

        if (busy.get()) {
            return;
        }

        busy.set(true);
        statusLabel.setText(message);

        Task<T> task = new Task<>() {

            @Override
            protected T call() throws Exception {

                return databaseWork.call();
            }
        };

        // Database operation succeeded

        task.setOnSucceeded(event -> {

            busy.set(false);

            try {

                requireStudent();

                onSuccess.accept(task.getValue());

            } catch (SecurityException ex) {

                courses.clear();
                sessions.clear();

                showError(
                        "Your session has ended. Please log in again.",
                        ex
                );

            } catch (RuntimeException ex) {

                showError(
                        "Unable to show attendance data.",
                        ex
                );
            }
        });

        // Database operation failed

        task.setOnFailed(event -> {

            busy.set(false);

            Throwable error = task.getException();

            if (error instanceof SecurityException) {

                courses.clear();
                sessions.clear();

                showError(
                        "Your session has ended. Please log in again.",
                        error
                );

            } else {

                showError(
                        "Could not load attendance. Check the database connection.",
                        error
                );
            }
        });

        Thread worker = new Thread(
                task,
                "ftms-student-attendance"
        );

        worker.setDaemon(true);
        worker.start();
    }

    // Display helpers

    private static ReadOnlyStringWrapper text(String value) {

        return new ReadOnlyStringWrapper(
                value == null ? "" : value
        );
    }

    private static String percent(Double value) {

        return value == null
                ? "N/A"
                : String.format(
                Locale.ROOT,
                "%.2f%%",
                value
        );
    }

    private static String progress(
            int present,
            int total,
            Double percentage) {

        return present + "/" + total
                + " (" + percent(percentage) + ")";
    }

    private static String time(SessionAttendance row) {

        String start = row.startTime() == null
                ? "-"
                : row.startTime().format(TIME_FORMAT);

        String end = row.endTime() == null
                ? "-"
                : row.endTime().format(TIME_FORMAT);

        return start + " - " + end;
    }

    // Error dialog

    private void showError(
            String message,
            Throwable exception) {

        statusLabel.setText(message);

        if (exception != null) {

            exception.printStackTrace();
        }

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        alert.setTitle("Student Attendance");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
