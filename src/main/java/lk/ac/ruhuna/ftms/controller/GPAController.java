package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.*;
import lk.ac.ruhuna.ftms.model.SemesterResult;
import lk.ac.ruhuna.ftms.service.GPAService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Existing GPA screen: authorized published snapshots and lecturer persistence. */
public class GPAController {
    @FXML private ComboBox<StudentOption> studentCombo;
    @FXML private ComboBox<SemesterOption> semesterCombo;
    @FXML private TableView<GpaCourseRow> courseTable;
    @FXML private TableColumn<GpaCourseRow,String> courseCodeColumn,courseNameColumn,creditsColumn,
            gradeColumn,gradePointColumn,qualityPointsColumn,statusColumn;
    @FXML private Label registeredCreditsLabel,earnedCreditsLabel,totalQualityPointsLabel,sgpaLabel,cgpaLabel,statusLabel,
            includedCreditsLabel,savedStateLabel,accessLabel;
    @FXML private TextArea assignmentDetails;
    @FXML private Button calculateButton,saveButton,refreshButton,clearButton,backButton,reportsButton;
    @FXML private ProgressIndicator progressIndicator;

    private final SessionManager.SessionIdentity identity=SessionManager.requireSession();
    private final GPAService gpaService;
    private final BooleanProperty busy=new SimpleBooleanProperty();
    private final ObjectProperty<SemesterResult> calculatedResult=new SimpleObjectProperty<>();
    private Set<String> repeatedCourses=Set.of();
    private Task<?> activeTask;
    private boolean disposed;
    private boolean studentView;
    private boolean updatingSelections;

    public GPAController(){this(new GPAService());}
    public GPAController(GPAService gpaService){this.gpaService=Objects.requireNonNull(gpaService);}

    @FXML public void initialize(){
        requireScreenSession();
        studentView="STUDENT".equals(SessionManager.getCurrentUser().getRole());
        courseTable.setEditable(false);
        courseTable.setPlaceholder(new Label("Select a student and semester to view published course results."));
        column(courseCodeColumn,GpaCourseRow::courseCode);
        column(courseNameColumn,GpaCourseRow::courseName);
        column(creditsColumn,r->number(r.credits()));
        column(gradeColumn,GpaCourseRow::gradeLetter);
        column(gradePointColumn,r->number(r.gradePoint()));
        column(qualityPointsColumn,r->repeatedCourses.contains(r.courseCode()) ? "Pending" : number(GPAService.qualityPoints(r)));
        column(statusColumn,r->repeatedCourses.contains(r.courseCode()) ? "Repeat policy pending - excluded"
                : r.published() && List.of("PASS","FAIL").contains(Objects.toString(r.resultStatus(),"")) && r.gradePoint()!=null
                ? r.resultStatus()+" - Published / included" : "Unpublished or incomplete - excluded");
        studentCombo.disableProperty().bind(busy.or(new SimpleBooleanProperty(studentView)));
        semesterCombo.disableProperty().bind(busy.or(studentCombo.valueProperty().isNull()));
        courseTable.disableProperty().bind(busy);
        calculateButton.disableProperty().bind(busy.or(semesterCombo.valueProperty().isNull()));
        saveButton.disableProperty().bind(busy.or(calculatedResult.isNull()));
        refreshButton.disableProperty().bind(busy);
        reportsButton.disableProperty().bind(busy);
        reportsButton.setText(studentView?"My Reports":"Assigned Course Reports");
        clearButton.disableProperty().bind(busy);
        progressIndicator.visibleProperty().bind(busy);progressIndicator.managedProperty().bind(busy);
        saveButton.setVisible(!studentView);saveButton.setManaged(!studentView);
        calculateButton.setVisible(!studentView);calculateButton.setManaged(!studentView);
        clearButton.setVisible(!studentView);clearButton.setManaged(!studentView);
        studentCombo.valueProperty().addListener((value,old,student)->{
            if(updatingSelections)return;
            semesterCombo.getSelectionModel().clearSelection();semesterCombo.getItems().clear();clearDisplay();
            if(student!=null)loadSemesters(student,null);
        });
        semesterCombo.valueProperty().addListener((value,old,semester)->{
            if(updatingSelections)return;
            clearDisplay();if(semester!=null && studentCombo.getValue()!=null)loadSemester(false);
        });
        courseTable.sceneProperty().addListener((value,old,scene)->{
            if(old!=null && scene==null){disposed=true;if(activeTask!=null)activeTask.cancel(true);}
        });
        loadStudents(null,null);
    }
    private void column(TableColumn<GpaCourseRow,String> column,Function<GpaCourseRow,String> value){
        column.setCellValueFactory(cell->new ReadOnlyStringWrapper(Objects.toString(value.apply(cell.getValue()),"Pending")));
    }
    private void loadStudents(Integer previousStudent,Integer previousSemester){
        clearSelections();accessLabel.setText("");showAssignmentDetails("");
        run("Loading authorized students...",gpaService::getStudentSelection,selection->{
            var students=selection.students();
            accessLabel.setText(selection.message());
            showAssignmentDetails(selection.missingAssignments().stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.joining("\n")));
            updatingSelections=true;
            try {
                studentCombo.setItems(FXCollections.observableArrayList(students));
                var selected=students.stream().filter(s->Objects.equals(s.studentId(),previousStudent)).findFirst()
                        .orElse(studentView && !students.isEmpty()?students.getFirst():null);
                studentCombo.setValue(selected);
            } finally {updatingSelections=false;}
            statusLabel.setText(selection.blockedStudents()>0
                    ? (students.isEmpty()?"No accessible full transcripts. Review the assignment details above or open Assigned Course Reports."
                        : "Select an accessible student and semester. Assignment details are shown above.")
                    : selection.message());
            if(students.isEmpty())courseTable.setPlaceholder(new Label("No accessible students. See the access details above."));
            if(studentCombo.getValue()!=null)loadSemesters(studentCombo.getValue(),previousSemester);
        });
    }
    private void showAssignmentDetails(String text){
        assignmentDetails.setText(text);assignmentDetails.setVisible(!text.isEmpty());assignmentDetails.setManaged(!text.isEmpty());
    }
    private void loadSemesters(StudentOption student,Integer previousSemester){
        run("Loading semesters...",()->gpaService.getSemestersForStudent(student.studentId()),semesters->{
            semesterCombo.setItems(FXCollections.observableArrayList(semesters));
            statusLabel.setText(semesters.isEmpty()?"No accessible semester enrollments.":"Select a semester.");
            var selected=semesters.stream().filter(s->Objects.equals(s.semesterId(),previousSemester)).findFirst()
                    .orElse(studentView && !semesters.isEmpty()?semesters.getLast():null);
            semesterCombo.setValue(selected);
        });
    }
    private void loadSemester(boolean calculate){
        var student=studentCombo.getValue();var semester=semesterCombo.getValue();
        if(student==null || semester==null)return;
        calculatedResult.set(null);
        run(calculate?"Calculating current published SGPA / CGPA...":"Loading GPA...",
                ()->gpaService.getSemesterView(student.studentId(),semester.semesterId()),view->{
            show(view);
            if(calculate && !studentView)calculatedResult.set(view.result());
        });
    }
    @FXML private void handleCalculate(){loadSemester(true);}
    @FXML private void handleSave(){
        if(studentView || calculatedResult.get()==null)return;
        var requested=calculatedResult.get();calculatedResult.set(null);
        run("Recalculating and saving semester GPA history...",()->{
            gpaService.saveSemesterResult(requested);
            return gpaService.getSemesterView(requested.getStudentId(),requested.getSemesterId());
        },view->{show(view);statusLabel.setText("Semester GPA saved successfully. "+view.message());});
    }
    @FXML private void handleRefresh(){
        if(activeTask!=null)return;
        loadStudents(studentCombo.getValue()==null?null:studentCombo.getValue().studentId(),
                semesterCombo.getValue()==null?null:semesterCombo.getValue().semesterId());
    }
    @FXML private void handleClear(){
        studentCombo.getSelectionModel().clearSelection();semesterCombo.getItems().clear();clearDisplay();
        statusLabel.setText("Select a student and semester.");
    }
    private void show(GPAService.SemesterView view){
        repeatedCourses=view.repeatedCourses();
        var published=view.rows().stream().filter(GpaCourseRow::published).toList();
        courseTable.getItems().setAll(published);courseTable.refresh();
        var result=view.result();
        registeredCreditsLabel.setText(number(result.getTotalRegisteredCredits()));
        earnedCreditsLabel.setText(number(result.getTotalEarnedCredits()));
        totalQualityPointsLabel.setText(number(result.getTotalQualityPoints()));
        sgpaLabel.setText(number(result.getSgpa()));cgpaLabel.setText(number(result.getCgpa()));
        includedCreditsLabel.setText("Included semester credits: "+number(view.includedCredits())
                +" | Excluded enrollment results: "+view.excludedCourses());
        savedStateLabel.setText(view.savedCurrent()?"Saved and current | Calculated "+result.getCalculatedAt()
                : "Current published-results calculation - saved summary is unavailable or stale.");
        statusLabel.setText((published.isEmpty()?"No published course results in this semester. SGPA is Pending. ":"")+view.message());
        courseTable.setPlaceholder(new Label("No published course results in this semester."));
    }
    private void clearDisplay(){
        calculatedResult.set(null);courseTable.getItems().clear();repeatedCourses=Set.of();
        for(var label:List.of(registeredCreditsLabel,earnedCreditsLabel,totalQualityPointsLabel,sgpaLabel,cgpaLabel))label.setText("Pending");
        includedCreditsLabel.setText("");savedStateLabel.setText("");
    }
    private void clearSelections(){
        updatingSelections=true;
        try {
            studentCombo.getSelectionModel().clearSelection();studentCombo.getItems().clear();
            semesterCombo.getSelectionModel().clearSelection();semesterCombo.getItems().clear();clearDisplay();
        } finally {updatingSelections=false;}
    }
    private static String number(Double value){return value==null?"Pending":String.format(Locale.ROOT,"%.2f",value);}
    private void requireScreenSession(){
        SessionManager.requireSession(identity);var user=SessionManager.getCurrentUser();
        if(user==null || !user.isActive() || !Set.of("LECTURER","STUDENT").contains(user.getRole()))
            throw new SecurityException("An active lecturer or student session is required for GPA.");
    }
    private <T> void run(String message,Callable<T> work,Consumer<T> success){
        if(disposed || activeTask!=null)return;
        try{requireScreenSession();}catch(SecurityException error){clearSelections();showAssignmentDetails("");accessLabel.setText("");statusLabel.setText(error.getMessage());return;}
        busy.set(true);statusLabel.setText(message);
        Task<T> task=new Task<>(){protected T call()throws Exception{requireScreenSession();return work.call();}};
        activeTask=task;
        task.setOnSucceeded(event->{if(accept(task))success.accept(task.getValue());});
        task.setOnFailed(event->{
            if(!accept(task))return;
            clearSelections();showAssignmentDetails("");accessLabel.setText("");
            String failure=failureMessage(task.getException());
            statusLabel.setText(failure);courseTable.setPlaceholder(new Label(failure));
        });
        task.setOnCancelled(event->{if(accept(task))statusLabel.setText("Operation cancelled. Refresh to check saved GPA.");});
        Thread worker=new Thread(task,"ftms-gpa");worker.setDaemon(true);worker.start();
    }
    private boolean accept(Task<?> task){
        if(disposed || activeTask!=task)return false;
        activeTask=null;busy.set(false);
        if(!SessionManager.isCurrentSession(identity)){clearSelections();showAssignmentDetails("");accessLabel.setText("");statusLabel.setText("Your session has ended. Sign in again.");return false;}
        return true;
    }
    static String failureMessage(Throwable error){
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if(cause instanceof SecurityException || cause instanceof IllegalArgumentException)return cause.getMessage();
            if(cause instanceof java.sql.SQLException sql)
                return sql instanceof java.sql.SQLTransientConnectionException
                        || sql instanceof java.sql.SQLNonTransientConnectionException
                        || (sql.getSQLState()!=null && sql.getSQLState().startsWith("08"))
                        ? "Database connection failed. Check the connection and Refresh."
                        : "Database query failed. Please Refresh or contact support; GPA was not loaded.";
            if(cause.getCause()==cause)break;
        }
        return "Unable to complete the GPA action. Refresh to check the latest saved state.";
    }
    @FXML private void handleBack(){
        navigate(studentView?"/fxml/student_dashboard.fxml":"/fxml/lecturer_dashboard.fxml","FTMS - Dashboard");
    }
    @FXML private void handleReports(){navigate("/fxml/reports.fxml","FTMS - Reports");}
    private void navigate(String resource,String title){
        try{requireScreenSession();NavigationUtil.navigate(courseTable,resource,title);}
        catch(IOException | SecurityException error){statusLabel.setText(error.getMessage());}
    }
    @FXML private void handleLogout(){
        if(SessionManager.isCurrentSession(identity))SessionManager.logout();
        else if(SessionManager.isLoggedIn()){clearDisplay();statusLabel.setText("This screen belongs to an earlier session.");return;}
        if(activeTask!=null)activeTask.cancel(true);clearDisplay();
        try{NavigationUtil.navigate(courseTable,"/fxml/login.fxml","FTMS - Login");}
        catch(IOException error){statusLabel.setText("You are logged out. Try Logout again to open sign in.");}
    }
}
