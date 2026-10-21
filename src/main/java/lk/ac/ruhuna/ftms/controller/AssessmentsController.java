package lk.ac.ruhuna.ftms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.concurrent.Task;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.service.AssessmentService;
import lk.ac.ruhuna.ftms.util.*;
import java.util.concurrent.Callable;

/** Form editing only; validation, authorization and persistence live in AssessmentService. */
public class AssessmentsController {
    @FXML private ComboBox<EligibilityOffering> offeringCombo;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField nameField,maxField,weightField;
    @FXML private TextArea descriptionField;
    @FXML private DatePicker dateField;
    @FXML private TableView<Assessment> assessmentTable;
    @FXML private TableColumn<Assessment,String> nameColumn,typeColumn;
    @FXML private TableColumn<Assessment,Double> maxColumn,weightColumn;
    @FXML private Label statusLabel;
    @FXML private Button saveButton,deleteButton,refreshButton;
    private final AssessmentService service=new AssessmentService();
    private final SessionManager.SessionIdentity identity=SessionManager.requireSession();
    @FXML public void initialize(){
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("assessmentName"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("assessmentType"));
        maxColumn.setCellValueFactory(new PropertyValueFactory<>("maxMark"));
        weightColumn.setCellValueFactory(new PropertyValueFactory<>("weightPercentage"));
        typeCombo.getItems().setAll(AssessmentService.TYPES);typeCombo.setValue("OTHER");
        offeringCombo.valueProperty().addListener((o,old,v)->handleRefresh());
        assessmentTable.getSelectionModel().selectedItemProperty().addListener((o,old,a)->{
            if(a==null)return;
            nameField.setText(a.getAssessmentName());typeCombo.setValue(a.getAssessmentType());
            maxField.setText(""+a.getMaxMark());weightField.setText(""+a.getWeightPercentage());
            dateField.setValue(a.getAssessmentDate());descriptionField.setText(a.getDescription());
        });
        run(()->{var rows=service.getOfferings();return ()->offeringCombo.getItems().setAll(rows);});
    }
    @FXML private void handleRefresh(){
        handleNew();var offering=offeringCombo.getValue();
        assessmentTable.getItems().clear();if(offering==null)return;
        run(()->{var rows=service.getAssessments(offering.offeringId());return ()->{
            assessmentTable.getItems().setAll(rows);statusLabel.setText("Assessment plan loaded. Results require a total weight of 100%.");};});
    }
    @FXML private void handleSave(){
        try{
            var offering=offeringCombo.getValue();if(offering==null)throw new IllegalArgumentException("Select an offering.");
            var old=assessmentTable.getSelectionModel().getSelectedItem();
            var a=new Assessment(old==null?0:old.getAssessmentId(),offering.offeringId(),nameField.getText(),typeCombo.getValue(),
                Double.parseDouble(maxField.getText().trim()),Double.parseDouble(weightField.getText().trim()),dateField.getValue(),descriptionField.getText());
            run(()->{service.save(a);var rows=service.getAssessments(offering.offeringId());return ()->{
                handleNew();assessmentTable.getItems().setAll(rows);statusLabel.setText("Assessment saved.");};});
        }catch(Exception error){statusLabel.setText(error.getMessage());}
    }
    @FXML private void handleDelete(){
        var selected=assessmentTable.getSelectionModel().getSelectedItem();if(selected==null)return;
        var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Delete the unreferenced assessment '"+selected.getAssessmentName()+"'?",ButtonType.CANCEL,ButtonType.OK);
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        run(()->{service.delete(selected.getOfferingId(),selected.getAssessmentId());var rows=service.getAssessments(selected.getOfferingId());
            return ()->{handleNew();assessmentTable.getItems().setAll(rows);statusLabel.setText("Assessment deleted.");};});
    }
    @FXML private void handleNew(){
        assessmentTable.getSelectionModel().clearSelection();nameField.clear();maxField.setText("100");
        weightField.clear();typeCombo.setValue("OTHER");dateField.setValue(null);descriptionField.clear();
    }
    @FXML private void handleBack()throws java.io.IOException{
        SessionManager.requireSession(identity);NavigationUtil.navigate(assessmentTable,"/fxml/marks_entry.fxml","FTMS - Marks Entry");
    }
    @FXML private void handleDashboard()throws java.io.IOException{
        SessionManager.requireSession(identity);
        NavigationUtil.navigate(assessmentTable,NavigationUtil.dashboardResource(),"FTMS - Dashboard");
    }
    private void run(Callable<Runnable> work){
        setBusy(true);
        Task<Runnable> task=new Task<>(){protected Runnable call()throws Exception{SessionManager.requireSession(identity);return work.call();}};
        task.setOnSucceeded(e->{setBusy(false);if(SessionManager.isCurrentSession(identity))task.getValue().run();else assessmentTable.getItems().clear();});
        task.setOnFailed(e->{setBusy(false);assessmentTable.getItems().clear();statusLabel.setText(task.getException().getMessage());});
        Thread thread=new Thread(task,"ftms-assessments");thread.setDaemon(true);thread.start();
    }
    private void setBusy(boolean busy){offeringCombo.setDisable(busy);saveButton.setDisable(busy);deleteButton.setDisable(busy);refreshButton.setDisable(busy);}
}
