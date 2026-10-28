package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse;
import lk.ac.ruhuna.ftms.model.CourseMaterial;
import lk.ac.ruhuna.ftms.service.StudentAccessService;
import lk.ac.ruhuna.ftms.service.StudentCourseMaterialService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class StudentCourseMaterialsController {
    @FXML private BorderPane studentMaterialsRoot;
    @FXML private Label courseCodeLabel, courseNameLabel, courseDetailsLabel, statusLabel, descriptionLabel;
    @FXML private TableView<CourseMaterial> materialsTable;
    @FXML private Button downloadButton, refreshButton;
    @FXML private ProgressIndicator progressIndicator;

    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();
    private final StudentAccessService access = new StudentAccessService(identity);
    private final StudentCourseMaterialService service = new StudentCourseMaterialService();
    private StudentCourse course;
    private Task<?> activeTask;
    private boolean disposed;

    @FXML public void initialize() {
        access.requireSession();
        column("Material Title", CourseMaterial::title, 240);
        column("Material Type", CourseMaterial::materialType, 150);
        column("Description", CourseMaterial::description, 280);
        column("Uploaded Date", m -> m.uploadedAt() == null ? "Not recorded"
                : m.uploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 155);
        column("Availability", CourseMaterial::availability, 310);
        materialsTable.setPlaceholder(new Label("Select a course from My Courses."));
        materialsTable.getSelectionModel().selectedItemProperty().addListener((value, old, material) -> {
            descriptionLabel.setText(material == null ? "Select a material to see its description."
                    : Objects.toString(material.description(), "No description provided."));
            updateDownloadButton();
        });
        studentMaterialsRoot.sceneProperty().addListener((value, old, scene) -> {
            if (old != null && scene == null) {
                disposed = true;
                if (activeTask != null) activeTask.cancel(true);
                materialsTable.getItems().clear();
            }
        });
        busy(false);
    }

    public void setCourse(StudentCourse selected) {
        access.requireSession();
        course = Objects.requireNonNull(selected);
        courseCodeLabel.setText(course.courseCode());
        courseNameLabel.setText(course.courseName());
        courseDetailsLabel.setText(course.semester() + " | Attempt " + course.attempt() + " | " + course.status());
        handleRefresh();
    }

    private void column(String title, Function<CourseMaterial, String> value, double width) {
        var column = new TableColumn<CourseMaterial, String>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Objects.toString(value.apply(cell.getValue()), "")));
        column.setPrefWidth(width); materialsTable.getColumns().add(column);
    }

    @FXML private void handleRefresh() {
        if (course == null || activeTask != null) return;
        materialsTable.getItems().clear();
        int offering = course.offeringId();
        run("Loading course materials...", () -> service.list(offering), materials -> {
            materialsTable.getItems().setAll(materials);
            materialsTable.setPlaceholder(new Label("No materials available for this course."));
            statusLabel.setText(materials.isEmpty() ? "No materials available for this course."
                    : materials.size() + " material(s). Select an available file to download.");
        });
    }

    @FXML private void handleDownload() {
        CourseMaterial material = materialsTable.getSelectionModel().getSelectedItem();
        if (course == null || material == null || activeTask != null) return;
        if (!"Available".equals(material.availability())) {
            statusLabel.setText(StudentCourseMaterialService.UNAVAILABLE); return;
        }
        var chooser = new FileChooser();
        chooser.setTitle("Save Course Material");
        chooser.setInitialFileName(StudentCourseMaterialService.suggestedFileName(material));
        var destination = chooser.showSaveDialog(studentMaterialsRoot.getScene().getWindow());
        if (destination == null) { statusLabel.setText("Download cancelled."); return; }
        int offering = course.offeringId();
        run("Downloading selected material...", () -> {
            service.export(offering, material.materialId(), destination.toPath()); return true;
        }, ignored -> statusLabel.setText("Material downloaded successfully."));
    }

    private <T> void run(String message, Callable<T> work, Consumer<T> success) {
        if (disposed || activeTask != null) return;
        try { access.requireSession(); } catch (SecurityException error) { deny(error.getMessage()); return; }
        var task = new Task<T>() { protected T call() throws Exception { return work.call(); } };
        activeTask = task; busy(true); statusLabel.setText(message);
        task.setOnSucceeded(event -> { if (accept(task)) success.accept(task.getValue()); });
        task.setOnFailed(event -> {
            if (!accept(task)) return;
            var error = task.getException();
            if (error instanceof SecurityException) deny(error.getMessage());
            else {
                String feedback = error instanceof IOException ? error.getMessage()
                        : "Unable to load or download materials. Please try Refresh.";
                statusLabel.setText(feedback);
                materialsTable.setPlaceholder(new Label(feedback));
            }
        });
        task.setOnCancelled(event -> { if (accept(task)) statusLabel.setText("Operation cancelled. Please try Refresh."); });
        Thread worker = new Thread(task, "student-course-materials"); worker.setDaemon(true); worker.start();
    }
    private boolean accept(Task<?> task) {
        if (disposed || activeTask != task) return false;
        activeTask = null; busy(false);
        if (!SessionManager.isCurrentSession(identity)) { deny("Your session has ended. Sign in again."); return false; }
        return true;
    }
    private void deny(String message) {
        materialsTable.getItems().clear(); statusLabel.setText(message);
        materialsTable.setPlaceholder(new Label(message));
    }
    private void busy(boolean busy) {
        refreshButton.setDisable(busy || course == null);
        progressIndicator.setVisible(busy); progressIndicator.setManaged(busy);
        updateDownloadButton();
    }
    private void updateDownloadButton() {
        var selected = materialsTable.getSelectionModel().getSelectedItem();
        downloadButton.setDisable(activeTask != null || selected == null || !"Available".equals(selected.availability()));
    }
    @FXML private void handleCourses() { navigate("student_courses.fxml", "My Courses"); }
    @FXML private void handleDashboard() { navigate("student_dashboard.fxml", "Student Dashboard"); }
    private void navigate(String resource, String title) {
        try {
            access.requireSession();
            NavigationUtil.navigate(studentMaterialsRoot, "/fxml/" + resource, "FTMS - " + title);
        } catch (IOException | SecurityException error) { statusLabel.setText(error.getMessage()); }
    }
    @FXML private void handleLogout() {
        if (SessionManager.isCurrentSession(identity)) SessionManager.logout();
        else if (SessionManager.isLoggedIn()) { deny("This screen belongs to an earlier session."); return; }
        if (activeTask != null) activeTask.cancel(true);
        materialsTable.getItems().clear();
        try { NavigationUtil.navigate(studentMaterialsRoot, "/fxml/login.fxml", "FTMS - Login"); }
        catch (IOException error) { statusLabel.setText("You are logged out. Try Logout again to open sign in."); }
    }
}
