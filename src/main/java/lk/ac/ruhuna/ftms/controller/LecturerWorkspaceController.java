package lk.ac.ruhuna.ftms.controller;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.util.*;
/** Shared read-only table shell, with dedicated marks/material editors only in those modes. */
public class LecturerWorkspaceController {
    @FXML private VBox workspaceRoot;
    @FXML private Label heading,message,fileLabel;
    @FXML private ComboBox<EligibilityOffering> offering;
    @FXML private TextField search,markValue,materialTitle;
    @FXML private TextArea description;
    @FXML private ComboBox<String> materialType;
    @FXML private ComboBox<CourseMaterials> materials;
    @FXML private TableView<List<String>> table;
    @FXML private HBox markEditor;
    @FXML private VBox materialEditor;
    @FXML private Button refresh;
    private final SessionManager.SessionIdentity identity=SessionManager.requireSession();
    private final LecturerViewService service;
    private final CourseMaterialsService materialService;
    private String mode;
    private Path upload;
    private boolean busy,disposed;
    private Task<?> task;
    public LecturerWorkspaceController() {
        this(new LecturerViewService(), new CourseMaterialsService());
    }
    public LecturerWorkspaceController(LecturerViewService service, CourseMaterialsService materialService) {
        this.service=Objects.requireNonNull(service);
        this.materialService=Objects.requireNonNull(materialService);
    }
    @FXML public void initialize() {
        var user=SessionManager.getCurrentUser();
        if(user==null || !user.isActive() || !"LECTURER".equals(user.getRole())) throw new SecurityException("Lecturer access required.");
        materialType.setItems(FXCollections.observableArrayList("LECTURE_NOTE","PRACTICAL","ASSIGNMENT","OTHER"));
        materialType.setValue("LECTURE_NOTE");
        offering.setOnAction(e->reload());
        materials.setOnAction(e->{
            var m=materials.getValue();upload=null;fileLabel.setText("No replacement selected");
            materialTitle.setText(m==null?"":m.title());description.setText(m==null?"":m.description());
            materialType.setValue(m==null?"LECTURE_NOTE":m.materialType());
        });
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if("marks".equals(mode) && b!=null) markValue.setText("—".equals(b.get(7))?"":b.get(7));
        });
        workspaceRoot.sceneProperty().addListener((o,a,b)->{if(a!=null && b==null){disposed=true;if(task!=null)task.cancel();}});
    }
    public void configure(String mode,String title) {
        this.mode=mode;heading.setText(title);
        markEditor.setVisible("marks".equals(mode));markEditor.setManaged(markEditor.isVisible());
        materialEditor.setVisible("materials".equals(mode));materialEditor.setManaged(materialEditor.isVisible());
        table.setVisible(!materialEditor.isVisible());table.setManaged(table.isVisible());
        run(service::offerings,rows->{offering.setItems(FXCollections.observableArrayList(rows));
            message.setText(rows.isEmpty()?"No assigned courses. Ask the administrator to verify lecturer course assignments.":"Choose an assigned course.");});
    }
    @FXML private void reload() {
        if(busy || offering.getValue()==null)return;
        table.setItems(FXCollections.observableArrayList());markValue.clear();
        int id=offering.getValue().offeringId();
        if("materials".equals(mode)) run(()->materialService.list(id),rows->{materials.setItems(FXCollections.observableArrayList(rows));newMaterial();message.setText(rows.size()+" materials. Select one to view/edit or upload a new file.");});
        else run(()->service.load(mode,id),data->{
            table.getColumns().clear();
            for(int i=0;i<data.columns().size();i++) {
                final int col=i;var column=new TableColumn<List<String>,String>(data.columns().get(i));
                column.setCellValueFactory(v->new ReadOnlyStringWrapper(v.getValue().get(col)));column.setPrefWidth(140);table.getColumns().add(column);
            }
            var filtered=new FilteredList<>(FXCollections.observableArrayList(data.rows()));
            filtered.predicateProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(()->row->row.stream().anyMatch(v->v.toLowerCase(Locale.ROOT).contains(search.getText().toLowerCase(Locale.ROOT))),search.textProperty()));
            table.setItems(filtered);
            message.setText(data.rows().size()+" records. "+switch(mode){
                case "results" -> "Published results only.";
                case "gpa" -> "Saved GPA for authorized students; totals with missing, unpublished or newer underlying results are withheld.";
                case "medical" -> "Course-specific dates and statuses only. General medical records are withheld.";
                case "marks" -> "Select an assessment row; blank mark records Pending.";
                default -> "Read-only records; missing values are shown as —.";
            });
        });
    }
    @FXML private void saveMark() {
        var row=table.getSelectionModel().getSelectedItem();if(row==null){message.setText("Select an assessment row.");return;}
        try {
            Double value=markValue.getText().isBlank()?null:Double.valueOf(markValue.getText().strip());
            var mark=new Mark(Integer.parseInt(row.get(0)),Integer.parseInt(row.get(2)),value,Integer.parseInt(row.get(6)));
            int id=offering.getValue().offeringId();
            run(()->{service.saveMark(id,mark);return true;},ok->reload());
        } catch(NumberFormatException e){message.setText("Enter a numeric mark.");}
    }
    @FXML private void newMaterial() {
        materials.getSelectionModel().clearSelection();materialTitle.clear();description.clear();
        materialType.setValue("LECTURE_NOTE");upload=null;fileLabel.setText("No file selected");
    }
    @FXML private void chooseFile() {
        var chooser=new FileChooser();chooser.setTitle("Upload course material (maximum 20 MiB)");
        var file=chooser.showOpenDialog(workspaceRoot.getScene().getWindow());
        if(file!=null){upload=file.toPath();fileLabel.setText(file.getName());}
    }
    @FXML private void saveMaterial() {
        if(offering.getValue()==null){message.setText("Choose an assigned course.");return;}
        int offeringId=offering.getValue().offeringId();var current=materials.getValue();int id=current==null?0:current.materialId();
        String title=materialTitle.getText(),text=description.getText(),type=materialType.getValue();Path source=upload;
        run(()->{materialService.save(offeringId,id,title,text,type,source);return true;},ok->reload());
    }
    @FXML private void download() {
        var current=materials.getValue();if(current==null){message.setText("Select a material.");return;}
        var chooser=new FileChooser();chooser.setTitle("Save material");
        String path=current.resourcePath();
        chooser.setInitialFileName(path!=null && path.matches("[0-9a-f-]{36}\\.[a-z0-9]+")?path:"material");
        var file=chooser.showSaveDialog(workspaceRoot.getScene().getWindow());if(file==null)return;
        run(()->{
            SessionManager.requireSession(identity);
            materialService.export(current.offeringId(),current.materialId(),file.toPath());return true;
        },ok->message.setText("Material downloaded."));
    }
    @FXML private void dashboard(){navigate(false);}
    @FXML private void logout(){navigate(true);}
    private void navigate(boolean logout) {
        if(logout) {
            SessionManager.logout();
            table.setItems(FXCollections.observableArrayList());
            materials.getItems().clear();
            materialTitle.clear();description.clear();markValue.clear();
        }
        try{NavigationUtil.navigate(workspaceRoot,logout?"/fxml/login.fxml":NavigationUtil.dashboardResource(),logout?"FTMS - Login":"FTMS - Lecturer Dashboard");}
        catch(Exception e){message.setText("Unable to open screen. Please retry.");}
    }
    private <T> void run(Callable<T> work,Consumer<T> success) {
        if(busy)return;SessionManager.requireSession(identity);setBusy(true);message.setText("Loading…");
        Task<T> next=new Task<>(){@Override protected T call() throws Exception{return work.call();}};task=next;
        next.setOnSucceeded(e->{if(disposed || !SessionManager.isCurrentSession(identity))return;setBusy(false);success.accept(next.getValue());});
        next.setOnFailed(e->{if(disposed || !SessionManager.isCurrentSession(identity))return;setBusy(false);
            var error=next.getException();message.setText(error instanceof SecurityException
                    || error instanceof IllegalArgumentException
                    || error instanceof lk.ac.ruhuna.ftms.exception.InvalidMarkException
                    || error instanceof java.io.IOException ? error.getMessage()
                    :"Unable to complete request. Check database access, schema and lecturer assignments, then retry.");});
        var thread=new Thread(next,"lecturer-workspace");thread.setDaemon(true);thread.start();
    }
    private void setBusy(boolean value){busy=value;offering.setDisable(value);refresh.setDisable(value);markEditor.setDisable(value);materialEditor.setDisable(value);}
}
