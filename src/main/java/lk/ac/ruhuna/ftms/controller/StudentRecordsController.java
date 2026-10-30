package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Student-only read views. No student selector, editable cells, or management actions. */
public class StudentRecordsController {
    @FXML private BorderPane studentRecordsRoot;
    @FXML private Label titleLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label statusLabel;
    @FXML private Label summaryLabel;
    @FXML private VBox summaryBox;
    @FXML private TableView<List<String>> summaryTable;
    @FXML private TableView<List<String>> recordsTable;
    @FXML private Button refreshButton;
    @FXML private ProgressIndicator progressIndicator;

    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();
    private final StudentAccessService access = new StudentAccessService(identity);
    private final ResultService resultService;
    private Task<View> activeTask;
    private final PauseTransition loadTimeout = new PauseTransition(Duration.seconds(30));
    private boolean disposed;
    private String feature;
    // Identity keys preserve the original record even when visible rows are equal or sorted.
    private java.util.Map<List<String>, StudentCourse> courseRows = new java.util.IdentityHashMap<>();
    private Button viewMaterialsButton;

    private record Table(List<String> columns, List<List<String>> rows) {}
    private record View(Table details, Table summary, String emptyMessage,
                        java.util.Map<List<String>, StudentCourse> courses) {
        View(Table details, Table summary, String emptyMessage) {
            this(details, summary, emptyMessage, java.util.Map.of());
        }
    }

    public StudentRecordsController() { this(new ResultService()); }
    StudentRecordsController(ResultService resultService) {
        this.resultService = Objects.requireNonNull(resultService);
    }

    @FXML public void initialize() {
        access.requireSession();
        feature = Objects.toString(studentRecordsRoot.getUserData());
        if ("assessments".equals(feature)) {
            var description = (TextArea) studentRecordsRoot.lookup("#assessmentDescription");
            recordsTable.getSelectionModel().selectedItemProperty().addListener((value, old, selected) ->
                    description.setText(selected == null ? "" : selected.get(8)));
        }
        if ("courses".equals(feature)) {
            viewMaterialsButton = (Button) studentRecordsRoot.lookup("#viewMaterialsButton");
            viewMaterialsButton.setDisable(true);
            recordsTable.getSelectionModel().selectedItemProperty().addListener((value, old, selected) ->
                    viewMaterialsButton.setDisable(activeTask != null || !courseRows.containsKey(selected)));
        }
        titleLabel.setText(switch (feature) {
            case "attendance" -> "My Attendance";
            case "medical" -> "My Medical";
            case "courses" -> "My Courses";
            case "assessments" -> "My Assessments";
            case "results" -> "My Grades and GPA";
            default -> throw new IllegalArgumentException("Unknown student view.");
        });
        descriptionLabel.setText(switch (feature) {
            case "attendance" -> "Your recorded sessions and attendance percentages. Unmarked sessions remain unmarked.";
            case "medical" -> "Your medical records and their current stored review statuses.";
            case "courses" -> "Your enrolled courses, semesters, credits and enrollment status.";
            case "assessments" -> "Released assessment details for your enrolled courses. View published marks separately in My Grades and GPA.";
            default -> "Your published grades and GPA. GPA follows the existing academic rules; unresolved values remain Pending.";
        });
        summaryLabel.setText("attendance".equals(feature) ? "Attendance by course" : "Semester GPA");
        summaryBox.setVisible(false);
        summaryBox.setManaged(false);
        studentRecordsRoot.sceneProperty().addListener((observable, old, scene) -> {
            if (old != null && scene == null) {
                disposed = true;
                Task<View> task = activeTask;
                activeTask = null;
                loadTimeout.stop();
                if (task != null) task.cancel(true);
                setBusy(false);
                clear();
            }
        });
        handleRefresh();
    }

    @FXML private void handleRefresh() {
        if (disposed || activeTask != null) return;
        clear();
        try { access.requireSession(); }
        catch (SecurityException error) { showMessage(error.getMessage()); return; }
        setBusy(true);
        statusLabel.setText("Loading your records...");
        recordsTable.setPlaceholder(new Label("Loading your records..."));
        activeTask = new Task<>() {
            @Override protected View call() throws Exception {
                access.requireSession();
                View view = loadView();
                access.requireSession();
                return view;
            }
        };
        Task<View> task = activeTask;
        task.setOnSucceeded(event -> {
            if (!accept(task)) return;
            View view = task.getValue();
            courseRows = new java.util.IdentityHashMap<>(view.courses());
            populate(recordsTable, view.details());
            boolean summary = view.summary() != null && !view.summary().rows().isEmpty();
            summaryBox.setManaged(summary);
            summaryBox.setVisible(summary);
            if (summary) populate(summaryTable, view.summary());
            recordsTable.setPlaceholder(new Label(view.emptyMessage()));
            statusLabel.setText(view.details().rows().isEmpty() ? view.emptyMessage()
                    : view.details().rows().size() + " record(s). Read-only access.");
            setBusy(false);
        });
        task.setOnFailed(event -> {
            if (!accept(task)) return;
            clear();
            Throwable error = task.getException();
            while (!(error instanceof SecurityException) && error.getCause() != null && error.getCause() != error)
                error = error.getCause();
            String message = error instanceof SecurityException ? error.getMessage()
                    : "Unable to load your records: " + Objects.toString(error.getMessage(),error.getClass().getSimpleName())
                    + ". Please try Refresh.";
            statusLabel.setText(message);
            recordsTable.setPlaceholder(new Label(message));
            setBusy(false);
        });
        task.setOnCancelled(event -> {
            if (!accept(task)) return;
            clear();
            showMessage("Loading cancelled. Please try Refresh.");
        });
        loadTimeout.setOnFinished(event -> {
            if (!accept(task)) return;
            task.cancel(true);
            clear();
            showMessage("Loading took too long. Please check the database connection and try Refresh.");
        });
        loadTimeout.playFromStart();
        Thread worker = new Thread(task, "student-" + feature);
        worker.setDaemon(true);
        worker.start();
    }

    private boolean accept(Task<View> task) {
        if (disposed || activeTask != task) return false;
        activeTask = null;
        loadTimeout.stop();
        setBusy(false);
        if (!SessionManager.isCurrentSession(identity)) {
            clear();
            showMessage("Your session has ended. Please sign in again.");
            return false;
        }
        return true;
    }

    private View loadView() throws Exception {
        return switch (feature) {
            case "attendance" -> attendance();
            case "medical" -> medical();
            case "courses" -> courses();
            case "assessments" -> assessments();
            case "results" -> results();
            default -> throw new IllegalStateException("Unknown student view.");
        };
    }

    private View courses() throws Exception {
        var rows = new ArrayList<List<String>>();
        var mapping = new java.util.IdentityHashMap<List<String>, StudentCourse>();
        for (var c : new StudentCourseService().getMyCourses()) {
            var display = row(c.courseCode(),c.courseName(),c.semester(),c.credits(),c.attempt(),c.status());
            rows.add(display);
            mapping.put(display, c);
        }
        return new View(new Table(List.of("Course code","Course name","Semester","Credits","Attempt","Status"),rows),
                null,"No enrolled courses available.", mapping);
    }

    private View assessments() throws Exception {
        var rows = new ArrayList<List<String>>();
        for (var released : new StudentAssessmentService(identity).getMyAssessments()) {
            var c = released.course(); var a = released.assessment();
            rows.add(row(c.courseCode()+" - "+c.courseName(),c.semester(),c.offeringId(),
                    a.getAssessmentName(),a.getAssessmentType(),a.getMaxMark(),a.getWeightPercentage(),
                    a.getAssessmentDate(),a.getDescription(),a.getPublicationStatus(),a.getPublishedAt()));
        }
        return new View(new Table(List.of("Enrolled Course","Semester","Offering","Assessment Name","Assessment Type",
                "Maximum Marks","Weight Percentage","Assessment Date","Description","Released Status","Released At"),rows),
                null,"No released assessments are available for your enrolled courses.");
    }

    private View medical() throws Exception {
        var rows = new ArrayList<List<String>>();
        for (var m : new MedicalService().getMyMedicals(identity))
            rows.add(row(m.getMedicalId(),m.getOfferingId(),m.getFromDate(),m.getToDate(),
                    m.getReason(),m.getStatus(),m.getSubmittedAt(),m.getReviewedAt(),m.getReviewNote()));
        return new View(new Table(List.of("Record","Course offering","From","To","Reason","Status",
                "Submitted","Reviewed","Review note"),rows),null,"No medical records available.");
    }

    private View attendance() throws Exception {
        var attendance = new AttendanceService().getMyAttendance();
        var summaries = new ArrayList<List<String>>();
        for (var entry : attendance.summaries()) {
            var c = entry.course(); var a = entry.attendance();
            summaries.add(row(c.courseCode(),c.courseName(),c.semester(),c.attempt(),
                    a.getTheoryPresent()+" / "+a.getTheorySessions(),percent(a.getTheoryPercentage()),
                    a.getPracticalPresent()+" / "+a.getPracticalSessions(),percent(a.getPracticalPercentage()),
                    percent(a.getCombinedPercentage())));
        }
        var sessions = new ArrayList<List<String>>();
        for (var s : attendance.sessions()) sessions.add(row(s.courseCode(),s.courseName(),s.semester(),
                s.attempt(),s.date(),s.type(),s.topic(),Objects.toString(s.status(),"UNMARKED")));
        return new View(new Table(List.of("Course code","Course name","Semester","Attempt","Date","Type","Topic","Status"),sessions),
                new Table(List.of("Course code","Course name","Semester","Attempt","Theory present / sessions","Theory %",
                        "Practical present / sessions","Practical %","Combined %"),summaries),"No attendance sessions available.");
    }

    private View results() throws Exception {
        var published = resultService.getMyPublishedRecords(identity);
        var rows = new ArrayList<List<String>>();
        for (var r : published.grades()) rows.add(row(r.courseCode(),r.courseName(),r.semester(),r.grade(),
                r.gradePoint(),r.credits(),r.attempt()));
        var summaries = new ArrayList<List<String>>();
        for (var semester : published.semesters())
            summaries.add(row(semester.semester(),decimal(semester.result().getSgpa()),decimal(semester.result().getCgpa()),semester.status()));
        return new View(new Table(List.of("Course code","Course name","Semester","Published grade","Grade point","Credits","Attempt"),rows),
                new Table(List.of("Semester","SGPA","CGPA","GPA status"),summaries),"No published results available.");
    }

    private static List<String> row(Object... values) {
        return java.util.Arrays.stream(values).map(value -> Objects.toString(value,"—")).toList();
    }
    private static String decimal(Double value) { return value == null ? "Pending" : String.format(Locale.ROOT,"%.2f",value); }
    private static String percent(Double value) { return value == null ? "N/A" : decimal(value)+"%"; }

    private static void populate(TableView<List<String>> table, Table data) {
        table.setEditable(false);
        table.getColumns().clear();
        for (int index = 0; index < data.columns().size(); index++) {
            int columnIndex = index;
            var column = new TableColumn<List<String>,String>(data.columns().get(index));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(columnIndex)));
            column.setEditable(false);
            column.setPrefWidth(index == 1 || data.columns().get(index).equals("Semester") ? 210 : 140);
            table.getColumns().add(column);
        }
        table.getItems().setAll(data.rows());
        table.setPlaceholder(new Label("No records available."));
    }

    private void clear() {
        courseRows.clear();
        if (viewMaterialsButton != null) viewMaterialsButton.setDisable(true);
        recordsTable.getItems().clear();
        summaryTable.getItems().clear();
        summaryBox.setVisible(false);
        summaryBox.setManaged(false);
    }
    private void showMessage(String message) {
        statusLabel.setText(message);
        recordsTable.setPlaceholder(new Label(message));
    }
    private void setBusy(boolean busy) {
        if (viewMaterialsButton != null) viewMaterialsButton.setDisable(busy
                || !courseRows.containsKey(recordsTable.getSelectionModel().getSelectedItem()));
        refreshButton.setDisable(busy);
        progressIndicator.setVisible(busy);
        progressIndicator.setManaged(busy);
    }

    @FXML private void handleDashboard() {
        try {
            access.requireSession();
            NavigationUtil.navigate(studentRecordsRoot,"/fxml/student_dashboard.fxml","FTMS - Student Dashboard");
        } catch (IOException | SecurityException error) { clear(); statusLabel.setText(error.getMessage()); }
    }

    @FXML private void handleViewMaterials() {
        StudentCourse course = courseRows.get(recordsTable.getSelectionModel().getSelectedItem());
        if (course == null || activeTask != null) return;
        try {
            access.requireSession();
            NavigationUtil.navigate(studentRecordsRoot, "/fxml/student_materials.fxml", "FTMS - Course Materials",
                    controller -> ((StudentCourseMaterialsController) controller).setCourse(course));
        } catch (IOException | SecurityException error) { statusLabel.setText(error.getMessage()); }
    }

    @FXML private void handleLogout() {
        if (SessionManager.isCurrentSession(identity)) SessionManager.logout();
        else if (SessionManager.isLoggedIn()) {
            clear(); statusLabel.setText("This screen belongs to an earlier session."); return;
        }
        clear();
        if (activeTask != null) activeTask.cancel(true);
        try { NavigationUtil.navigate(studentRecordsRoot,"/fxml/login.fxml","FTMS - Login"); }
        catch (IOException error) { statusLabel.setText("You are logged out. Please try Logout again to open sign in."); }
    }
}
