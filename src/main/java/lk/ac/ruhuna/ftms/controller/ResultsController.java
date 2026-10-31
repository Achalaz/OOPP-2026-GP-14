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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lk.ac.ruhuna.ftms.exception.ResultCalculationException;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.ResultRow;
import lk.ac.ruhuna.ftms.service.ResultService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** JavaFX interaction only. Result rules and persistence are accessed through ResultService. */
public class ResultsController {
    @FXML private ComboBox<EligibilityOffering> offeringCombo;
    @FXML private ComboBox<ResultRow> enrollmentCombo;
    @FXML private Label caMarkLabel;
    @FXML private Label finalExamMarkLabel;
    @FXML private Label overallMarkLabel;
    @FXML private Label resultStatusLabel;
    @FXML private Label gradeLetterLabel;
    @FXML private Label gradePointLabel;
    @FXML private Label resultStateLabel;
    @FXML private Label reasonLabel;
    @FXML private Label statusLabel;
    @FXML private Label publicationPolicyLabel;
    @FXML private Label eligibilityLabel;
    @FXML private javafx.scene.control.ProgressIndicator progressIndicator;
    @FXML private Button calculateButton;
    @FXML private Button saveButton;
    @FXML private Button publishButton;
    @FXML private Button clearButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private TableView<ResultRow> batchTable;
    @FXML private TableColumn<ResultRow, String> enrollmentColumn;
    @FXML private TableColumn<ResultRow, String> studentColumn;
    @FXML private TableColumn<ResultRow, String> caMarkColumn;
    @FXML private TableColumn<ResultRow, String> finalExamMarkColumn;
    @FXML private TableColumn<ResultRow, String> overallMarkColumn;
    @FXML private TableColumn<ResultRow, String> resultStatusColumn;
    @FXML private TableColumn<ResultRow, String> gradeLetterColumn;
    @FXML private TableColumn<ResultRow, String> gradePointColumn;
    @FXML private TableColumn<ResultRow, String> stateColumn;
    @FXML private TableColumn<ResultRow, String> eligibilityColumn;

    private final SessionManager.SessionIdentity screenIdentity = SessionManager.requireSession();
    private final ResultService resultService;
    private final ObservableList<ResultRow> rows = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final ObjectProperty<Integer> readyEnrollment = new SimpleObjectProperty<>();
    private final BooleanProperty publicationAuthorized = new SimpleBooleanProperty();
    private final BooleanProperty publishableSelection = new SimpleBooleanProperty();
    private final BooleanProperty publishedSelection = new SimpleBooleanProperty();
    private boolean updatingSelection;
    private Task<?> activeTask;
    private boolean disposed;
    private record Batch(java.util.List<ResultRow> rows, boolean publicationAllowed) {}

    public ResultsController() { this(new ResultService()); }

    public ResultsController(ResultService resultService) {
        this.resultService = Objects.requireNonNull(resultService);
    }

    /** Compatibility hook only; audit identity always comes from the authenticated session. */
    @Deprecated
    public void setCalculatedBy(Integer userId) {
        if (userId != null && !Objects.equals(userId, SessionManager.getCurrentUserIdOrNull())) {
            throw new SecurityException("Calculated-by must be the signed-in user.");
        }
        readyEnrollment.set(null);
    }

    @FXML
    public void initialize() {
        requireScreenSession();
        enrollmentCombo.setItems(rows);
        batchTable.setItems(rows);
        batchTable.setPlaceholder(new Label("Select a course/offering to view enrollments."));
        column(enrollmentColumn, row -> Integer.toString(row.enrollmentId()));
        column(studentColumn, row -> row.registrationNo() + " - " + row.studentName());
        column(caMarkColumn, row -> number(row.result(), CourseResult::getCaMark));
        column(finalExamMarkColumn, row -> number(row.result(), CourseResult::getFinalExamMark));
        column(overallMarkColumn, row -> number(row.result(), CourseResult::getOverallMark));
        column(resultStatusColumn, row -> row.result() == null ? "PENDING" : row.result().getResultStatus().name());
        column(gradeLetterColumn, row -> grade(row.result()));
        column(gradePointColumn, row -> number(row.result(), CourseResult::getGradePoint));
        column(stateColumn, row -> state(row.result()));
        column(eligibilityColumn, ResultsController::eligibility);

        offeringCombo.disableProperty().bind(busy);
        enrollmentCombo.disableProperty().bind(busy.or(offeringCombo.valueProperty().isNull()));
        batchTable.disableProperty().bind(busy);
        calculateButton.disableProperty().bind(busy.or(enrollmentCombo.valueProperty().isNull()).or(publishedSelection));
        saveButton.disableProperty().bind(busy.or(readyEnrollment.isNull()));
        publishButton.visibleProperty().bind(publicationAuthorized);
        publishButton.managedProperty().bind(publicationAuthorized);
        publishButton.disableProperty().bind(busy.or(publicationAuthorized.not()).or(publishableSelection.not()));
        clearButton.disableProperty().bind(busy);
        refreshButton.disableProperty().bind(busy);
        progressIndicator.visibleProperty().bind(busy);
        progressIndicator.managedProperty().bind(busy);
        batchTable.sceneProperty().addListener((observable, old, scene) -> {
            if(old!=null && scene==null) {disposed=true;if(activeTask!=null)activeTask.cancel(true);}
        });

        offeringCombo.valueProperty().addListener((observable, previous, selected) -> loadEnrollments(selected));
        enrollmentCombo.valueProperty().addListener((observable, previous, selected) -> selectRow(selected));
        batchTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> selectRow(selected));
        display(null);
        loadOfferings();
    }

    private void column(TableColumn<ResultRow, String> column, Function<ResultRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private void loadOfferings() {
        runTask("Loading course offerings...", resultService::getOfferings, offerings -> {
            offeringCombo.setItems(FXCollections.observableArrayList(offerings));
            statusLabel.setText(offerings.isEmpty() ? "No course offerings are available."
                    : "Select a course/offering and a student/enrollment.");
        });
    }

    private void loadEnrollments(EligibilityOffering offering) {
        publicationAuthorized.set(false);
        readyEnrollment.set(null);
        rows.clear();
        selectRow(null);
        if (offering == null) {
            batchTable.setPlaceholder(new Label("Select a course/offering to view enrollments."));
            statusLabel.setText("Select a course/offering and a student/enrollment.");
            return;
        }
        batchTable.setPlaceholder(new Label("No enrollments found for this offering."));
        runTask("Loading batch results...", () -> new Batch(resultService.getBatchResults(offering.offeringId()),
                resultService.canPublishResult(offering.offeringId())), batch -> {
            publicationAuthorized.set(batch.publicationAllowed());
            rows.setAll(batch.rows());display(null);
            statusLabel.setText(batch.rows().isEmpty() ? "No enrollments found for this offering."
                    : batch.rows().size() + " enrollments loaded. Select one to view or calculate a result.");
        });
    }

    private void selectRow(ResultRow row) {
        if (updatingSelection) { return; }
        updatingSelection = true;
        try {
            readyEnrollment.set(null);
            if (row == null) {
                enrollmentCombo.getSelectionModel().clearSelection();
                batchTable.getSelectionModel().clearSelection();
            } else {
                enrollmentCombo.getSelectionModel().select(row);
                batchTable.getSelectionModel().select(row);
            }
            display(row);
        } finally { updatingSelection = false; }
    }

    @FXML
    private void handleCalculate() {
        ResultRow selected = enrollmentCombo.getValue();
        if (selected == null) { return; }
        int offering = offeringCombo.getValue().offeringId();
        readyEnrollment.set(null);
        runTask("Calculating result...", () -> refreshedRow(offering, selected,
                resultService.calculateResult(selected.enrollmentId())), row -> {
            replaceRow(row);
            readyEnrollment.set(selected.enrollmentId());
            statusLabel.setText("Result calculated. Save / Update recalculates and saves an unpublished draft.");
        });
    }

    @FXML
    private void handleSave() {
        ResultRow selected = enrollmentCombo.getValue();
        if (selected == null || !Objects.equals(readyEnrollment.get(), selected.enrollmentId())) { return; }
        int offering = offeringCombo.getValue().offeringId();
        readyEnrollment.set(null);
        runTask("Recalculating and saving result...",
                () -> refreshedRow(offering, selected, resultService.calculateAndSaveResult(selected.enrollmentId())), row -> {
                    replaceRow(row);
                    statusLabel.setText("Unpublished result saved for enrollment " + selected.enrollmentId() + ".");
                });
    }

    @FXML
    private void handlePublish() {
        ResultRow selected = enrollmentCombo.getValue();
        EligibilityOffering offering = offeringCombo.getValue();
        if (busy.get() || selected == null || selected.result() == null || offering == null
                || !publishableSelection.get() || !publicationAuthorized.get()) { return; }
        try { requireScreenSession(); }
        catch (SecurityException error) { statusLabel.setText(error.getMessage()); return; }
        var reviewed = selected.result().publicationSnapshot();
        var identity = SessionManager.requireSession();
        if (!confirmPublication(selected, reviewed)) { return; }
        readyEnrollment.set(null);
        runTask("Validating and publishing result...", () -> {
            SessionManager.requireSession(identity);
            return resultService.publishResult(offering.offeringId(), reviewed);
        }, result -> {
            replaceRow(selected.withResult(result));
            statusLabel.setText("Result published for enrollment " + selected.enrollmentId() + ".");
        });
    }

    /** Package-visible so the FXML runner can test confirm/cancel without opening a window. */
    boolean confirmPublication(ResultRow selected, CourseResult.PublicationSnapshot reviewed) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Publish Result");
        confirmation.setHeaderText("Publish the reviewed result for " + selected.registrationNo() + "?");
        confirmation.setContentText("Enrollment " + selected.enrollmentId() + " | " + offeringCombo.getValue()
                + "\nOverall: " + reviewed.overallMark() + " | Grade: " + reviewed.gradeLetter()
                + " | Status: " + reviewed.resultStatus()
                + "\nThis makes the saved result available to published-result views and GPA calculations."
                + " Current inputs will be checked again before publication.");
        if (publishButton.getScene() != null && publishButton.getScene().getWindow() != null) {
            confirmation.initOwner(publishButton.getScene().getWindow());
        }
        ((Button) confirmation.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        ((Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    @FXML private void handleGpa() { navigate("/fxml/gpa.fxml", "FTMS - SGPA / CGPA"); }
    @FXML private void handleReports() { navigate("/fxml/reports.fxml", "FTMS - Reports"); }
    private ResultRow refreshedRow(int offering,ResultRow selected,CourseResult calculated) {
        return resultService.getBatchResults(offering).stream()
                .filter(row -> row.enrollmentId()==selected.enrollmentId()).findFirst()
                .orElseThrow(() -> new ResultCalculationException("Enrollment is unavailable. Refresh the offering."))
                .withResult(calculated);
    }
    private void replaceRow(ResultRow replacement) {
        updatingSelection = true;
        try {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).enrollmentId() == replacement.enrollmentId()) {
                    rows.set(index, replacement);
                    break;
                }
            }
            enrollmentCombo.getSelectionModel().select(replacement);
            batchTable.getSelectionModel().select(replacement);
            display(replacement);
        } finally { updatingSelection = false; }
    }

    @FXML
    private void handleClear() {
        offeringCombo.getSelectionModel().clearSelection();
        rows.clear();
        selectRow(null);
        statusLabel.setText("Select a course/offering and a student/enrollment.");
    }

    @FXML
    private void handleRefresh() {
        if (offeringCombo.getValue() == null) { loadOfferings(); }
        else { loadEnrollments(offeringCombo.getValue()); }
    }

    @FXML
    private void handleBack() {
        navigate("/fxml/lecturer_dashboard.fxml", "FTMS - Lecturer Dashboard");
    }

    private void navigate(String resource,String title) {
        try { requireScreenSession();NavigationUtil.navigate(batchTable,resource,title); }
        catch(IOException | SecurityException error){statusLabel.setText(error.getMessage());}
    }
    @FXML private void handleLogout() {
        if(SessionManager.isCurrentSession(screenIdentity))SessionManager.logout();
        else if(SessionManager.isLoggedIn()){statusLabel.setText("This screen belongs to an earlier session.");return;}
        if(activeTask!=null)activeTask.cancel(true);
        rows.clear();
        try{NavigationUtil.navigate(batchTable,"/fxml/login.fxml","FTMS - Login");}
        catch(IOException error){statusLabel.setText("You are logged out. Try Logout again to open sign in.");}
    }
    private void requireScreenSession() {
        SessionManager.requireSession(screenIdentity);
        var user=SessionManager.getCurrentUser();
        if(user==null || !user.isActive() || !"LECTURER".equals(user.getRole()))
            throw new SecurityException("An active lecturer session is required for Result Publication.");
    }
    private static String eligibility(ResultRow row) {
        return row.overallEligible()==null ? "Pending" : row.overallEligible() ? "Eligible" : "Not eligible";
    }

    private void display(ResultRow row) {
        CourseResult result = row == null ? null : row.result();
        publishedSelection.set(result!=null && result.isPublished());
        String publicationBlock = ResultService.draftPublicationBlockReason(result);
        publishableSelection.set(publicationBlock == null);
        publicationPolicyLabel.setText(!publicationAuthorized.get() ? ResultService.PUBLICATION_POLICY_PENDING
                : row == null ? "Select an enrollment to review its saved result."
                : publicationBlock != null ? publicationBlock
                : "Review this saved draft before publishing. Current marks and eligibility will be checked again.");
        caMarkLabel.setText(number(result, CourseResult::getCaMark));
        finalExamMarkLabel.setText(number(result, CourseResult::getFinalExamMark));
        overallMarkLabel.setText(number(result, CourseResult::getOverallMark));
        resultStatusLabel.setText(row == null ? "--" : result == null ? "PENDING" : result.getResultStatus().name());
        gradeLetterLabel.setText(grade(result));
        gradePointLabel.setText(number(result, CourseResult::getGradePoint));
        resultStateLabel.setText(row == null ? "No enrollment selected" : row + " | " + state(result));
        reasonLabel.setText(row == null ? "Select an enrollment to view a result." : resultService.describe(result));
        eligibilityLabel.setText(row==null ? "Eligibility: select an enrollment." : "Eligibility: " + eligibility(row)
                + ". " + Objects.toString(row.eligibilityReason(),"No eligibility reason recorded."));
    }

    private static String number(CourseResult result, Function<CourseResult, BigDecimal> property) {
        BigDecimal value = result == null ? null : property.apply(result);
        return value == null ? "--" : value.toPlainString();
    }

    private static String grade(CourseResult result) {
        return result == null || result.getGradeLetter() == null ? "--" : result.getGradeLetter();
    }

    private static String state(CourseResult result) {
        if (result == null) { return "Not calculated"; }
        if (result.getResultId() == 0) { return "Calculated - not saved"; }
        return result.isPublished() ? "Published" : "Saved draft";
    }

    private <T> void runTask(String message, Callable<T> work, Consumer<T> onSuccess) {
        if(disposed || activeTask!=null)return;
        try{requireScreenSession();}catch(SecurityException error){rows.clear();display(null);statusLabel.setText(error.getMessage());return;}
        var identity = screenIdentity;
        busy.set(true);
        statusLabel.setText(message);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                if (identity != null) { SessionManager.requireSession(identity); }
                return work.call();
            }
        };
        activeTask=task;
        task.setOnSucceeded(event -> {
            if(!accept(task))return;
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if(!accept(task))return;
            readyEnrollment.set(null);
            rows.clear(); display(null);
            Throwable error = task.getException();
            showError(error instanceof ResultCalculationException || error instanceof SecurityException ? error.getMessage()
                    : "Unable to complete the result action. Check the database connection and retry.", error);
        });
        task.setOnCancelled(event -> {if(accept(task))statusLabel.setText("Operation cancelled. Refresh to check current records.");});
        Thread thread = new Thread(task, "ftms-results");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean accept(Task<?> task) {
        if(disposed || activeTask!=task)return false;
        activeTask=null;busy.set(false);
        if(!SessionManager.isCurrentSession(screenIdentity)) {
            rows.clear();display(null);statusLabel.setText("Your session has ended. Sign in again.");return false;
        }
        return true;
    }

    private void showError(String message, Throwable error) {
        statusLabel.setText(message);
        error.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(error instanceof ResultCalculationException ? "Result Validation" : "Results Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
