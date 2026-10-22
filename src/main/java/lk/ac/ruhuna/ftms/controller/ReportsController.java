package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.concurrent.Task;
import lk.ac.ruhuna.ftms.dao.ReportDAO.*;
import lk.ac.ruhuna.ftms.model.ResultReportRow;
import lk.ac.ruhuna.ftms.service.ReportService;
import lk.ac.ruhuna.ftms.util.*;
import java.util.*;
import java.util.concurrent.Callable;

public class ReportsController {
    @FXML private ComboBox<StudentOption> studentCombo;
    @FXML private ComboBox<OfferingOption> offeringCombo;
    @FXML private ComboBox<Integer> batchCombo;
    @FXML private TableView<ResultReportRow> reportTable;
    @FXML private TableColumn<ResultReportRow,Object> registrationColumn,studentNameColumn,courseCodeColumn,courseNameColumn,
        creditsColumn,caMarkColumn,finalMarkColumn,overallMarkColumn,gradeColumn,gradePointColumn,statusColumn;
    @FXML private Label totalLabel,passLabel,failLabel,pendingLabel,averageLabel,statusLabel;
    @FXML private Button studentReportButton,offeringReportButton,batchReportButton,refreshButton,clearButton,backButton;
    @FXML private TextArea detailsArea;
    @FXML private Label offeringLabel,batchLabel,titleLabel;
    private final ReportService reportService;
    private final SessionManager.SessionIdentity identity=SessionManager.requireSession();
    private final boolean studentView="STUDENT".equals(SessionManager.getCurrentUser().getRole());
    private Callable<List<ResultReportRow>> currentReport;
    public ReportsController(){this(new ReportService());}
    public ReportsController(ReportService service){reportService=Objects.requireNonNull(service);}
    @FXML public void initialize(){
        if(studentView){
            titleLabel.setText("My Published Results");
            studentReportButton.setText("Load My Results");
            for(javafx.scene.Node node:List.of(offeringLabel,offeringCombo,offeringReportButton,batchLabel,batchCombo,batchReportButton)){
                node.setVisible(false);node.setManaged(false);
            }
        }
        registrationColumn.setCellValueFactory(new PropertyValueFactory<>("registrationNo"));
        studentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        courseCodeColumn.setCellValueFactory(new PropertyValueFactory<>("courseCode"));
        courseNameColumn.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        creditsColumn.setCellValueFactory(new PropertyValueFactory<>("credits"));
        caMarkColumn.setCellValueFactory(new PropertyValueFactory<>("caMark"));
        finalMarkColumn.setCellValueFactory(new PropertyValueFactory<>("finalExamMark"));
        overallMarkColumn.setCellValueFactory(new PropertyValueFactory<>("overallMark"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("gradeLetter"));
        gradePointColumn.setCellValueFactory(new PropertyValueFactory<>("gradePoint"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("resultStatus"));
        reportTable.setPlaceholder(new Label("No published results or pending enrollments match this selection."));
        reportTable.getSelectionModel().selectedItemProperty().addListener((o,old,row)->{
            detailsArea.clear();if(row==null)return;
            run(()->{var details=reportService.getMarkDetails(row.getEnrollmentId());return ()->{
                if(reportTable.getSelectionModel().getSelectedItem()!=row)return;
                var text=new StringBuilder();
                for(var d:details)text.append(d.assessment()).append(" | ").append(d.type()).append(" | ")
                    .append(Objects.toString(d.mark(),"Pending")).append(" / ").append(d.maximum())
                    .append(" | Weight ").append(d.weight()).append("% | ").append(d.status()).append("\n");
                detailsArea.setText(details.isEmpty()?"No published assessment details.":text.toString());
            };});
        });
        summary(List.of());loadSelections();
    }
    private void loadSelections(){
        run(()->{var students=reportService.getStudents();var offerings=reportService.getCourseOfferings();var batches=reportService.getBatchYears();
            return ()->{studentCombo.getItems().setAll(students);offeringCombo.getItems().setAll(offerings);batchCombo.getItems().setAll(batches);
                statusLabel.setText("Reports show published marks and grades. Unpublished enrollments remain pending.");
                if(studentView && !students.isEmpty()){
                    studentCombo.getSelectionModel().selectFirst();handleStudentReport();
                }
            };});
    }
    @FXML private void handleStudentReport(){
        var s=studentCombo.getValue();if(s==null){statusLabel.setText("Select a student.");return;}
        currentReport=()->reportService.getStudentReport(s.studentId());loadReport();
    }
    @FXML private void handleOfferingReport(){
        var o=offeringCombo.getValue();if(o==null){statusLabel.setText("Select an offering.");return;}
        currentReport=()->reportService.getOfferingReport(o.offeringId());loadReport();
    }
    @FXML private void handleBatchReport(){
        var year=batchCombo.getValue();if(year==null){statusLabel.setText("Select an intake year.");return;}
        currentReport=()->reportService.getBatchReport(year);loadReport();
    }
    private void loadReport(){
        var selected=currentReport;reportTable.getItems().clear();detailsArea.clear();summary(List.of());
        run(()->{var rows=selected.call();return ()->{reportTable.getItems().setAll(rows);summary(rows);statusLabel.setText(rows.size()+" report rows loaded.");};});
    }
    private void summary(List<ResultReportRow> rows){
        totalLabel.setText(""+reportService.countTotalResults(rows));passLabel.setText(""+reportService.countPassed(rows));
        failLabel.setText(""+reportService.countFailed(rows));pendingLabel.setText(""+reportService.countPending(rows));
        var average=reportService.calculateAverageOverallMark(rows);
        averageLabel.setText(average==null?"--":String.format(Locale.ROOT,"%.2f",average));
    }
    @FXML private void handleRefresh(){if(currentReport==null)loadSelections();else loadReport();}
    @FXML private void handleClear(){currentReport=null;reportTable.getItems().clear();detailsArea.clear();summary(List.of());}
    @FXML private void handleBack()throws java.io.IOException{
        SessionManager.requireSession(identity);NavigationUtil.navigate(reportTable,NavigationUtil.dashboardResource(),"FTMS - Dashboard");
    }
    private void run(Callable<Runnable> work){
        busy(true);
        Task<Runnable> task=new Task<>(){protected Runnable call()throws Exception{SessionManager.requireSession(identity);return work.call();}};
        task.setOnSucceeded(e->{busy(false);if(SessionManager.isCurrentSession(identity))task.getValue().run();else handleClear();});
        task.setOnFailed(e->{busy(false);handleClear();statusLabel.setText(task.getException().getMessage());});
        Thread thread=new Thread(task,"ftms-report");thread.setDaemon(true);thread.start();
    }
    private void busy(boolean value){
        for(var button:List.of(studentReportButton,offeringReportButton,batchReportButton,refreshButton,clearButton,backButton))button.setDisable(value);
        studentCombo.setDisable(value);offeringCombo.setDisable(value);batchCombo.setDisable(value);
        if(studentView){offeringReportButton.setDisable(true);batchReportButton.setDisable(true);}
        reportTable.setDisable(value);
    }
}
