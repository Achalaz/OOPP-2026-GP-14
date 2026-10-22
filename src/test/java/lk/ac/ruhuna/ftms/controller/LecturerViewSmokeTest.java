package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.util.*;
import java.util.*;
import java.util.concurrent.*;

/** Real FXML and async controller tests with in-memory service fixtures. No database or visible window. */
public final class LecturerViewSmokeTest {
    private static int checks;
    private static Parent root;
    private static int saves;
    private static final User LECTURER=new User(7,"test","Test Lecturer","test@example.org","LECTURER",true);
    public static void main(String[] args) throws Exception {
        var started=new CountDownLatch(1);Platform.startup(started::countDown);
        if(!started.await(10,TimeUnit.SECONDS))throw new AssertionError("FX startup");
        try {
            fx(()->{
                SessionManager.startSession(LECTURER);
                check(NavigationUtil.dashboardResource().equals("/fxml/lecturer_dashboard.fxml"),"lecturer routing");
                var loader=new FXMLLoader(LecturerViewSmokeTest.class.getResource("/fxml/lecturer_dashboard.fxml"));
                root=loader.load();new Scene(root,1250,800);root.applyCss();root.layout();
                check(((Label)root.lookup("#welcomeLabel")).getText().contains("Test Lecturer"),"welcome name");
                try{NavigationUtil.navigate(root,"/fxml/notices.fxml","Manage notices");throw new AssertionError("Admin-only screen allowed");}
                catch(SecurityException expected){checks++;}
                return null;
            });
            for(String mode:List.of("students","eligibility","results","gpa","attendance","medical","marks","materials")) {
                fx(()->{
                    var loader=new FXMLLoader(LecturerViewSmokeTest.class.getResource("/fxml/lecturer_workspace.fxml"));
                    loader.setControllerFactory(t->new LecturerWorkspaceController(new StubViews(),new StubMaterials()));
                    root=loader.load();new Scene(root,1200,800);root.applyCss();root.layout();
                    ((LecturerWorkspaceController)loader.getController()).configure(mode,mode);
                    return null;
                });
                await(()->!((ComboBox<?>)root.lookup("#offering")).isDisabled());
                fx(()->{((ComboBox<?>)root.lookup("#offering")).getSelectionModel().selectFirst();return null;});
                await(()->!((ComboBox<?>)root.lookup("#offering")).isDisabled());
                fx(()->{
                    check(root.lookup("#markEditor").isVisible()==mode.equals("marks"),"marks controls: "+mode);
                    check(root.lookup("#materialEditor").isVisible()==mode.equals("materials"),"material controls: "+mode);
                    if(!mode.equals("materials")) {
                        var table=(TableView<?>)root.lookup("#table");
                        check(table.getItems().size()==1,"loaded "+mode);
                        check(!table.isEditable(),"read-only table");
                        if(mode.equals("marks")) {
                            table.getSelectionModel().selectFirst();
                            ((TextField)root.lookup("#markValue")).setText("55");
                            button("Save Mark").fire();
                        }
                    }
                    return null;
                });
                await(()->!((ComboBox<?>)root.lookup("#offering")).isDisabled());
                fx(()->{((Button)root.lookup("#refresh")).fire();return null;});
                await(()->!((ComboBox<?>)root.lookup("#offering")).isDisabled());
            }
            fx(()->{
                check(saves==1,"marks save delegates exactly once");
                button("Log Out").fire();check(!SessionManager.isLoggedIn(),"logout invalidates session");
                for(String file:List.of("lecturer_dashboard.fxml","lecturer_workspace.fxml")) {
                    try{FXMLLoader.load(LecturerViewSmokeTest.class.getResource("/fxml/"+file));throw new AssertionError("Unauthenticated screen allowed");}
                    catch(java.io.IOException expected){checks++;}
                }
                SessionManager.startSession(new User(1,"admin","Admin","a@example.org","ADMIN",true));
                check(NavigationUtil.dashboardResource().equals("/fxml/admin_dashboard.fxml"),"admin routing");
                SessionManager.logout();return null;
            });
            System.out.println("Lecturer FXML/controller checks passed: "+checks);
        } finally {Platform.exit();}
    }
    private static Button button(String text) {
        return root.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow();
    }
    private static <T>T fx(Callable<T> work) throws Exception {var task=new FutureTask<T>(work);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition) throws Exception {
        for(int i=0;i<100;i++){if(fx(condition))return;Thread.sleep(50);}throw new AssertionError("Async operation timed out");
    }
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    private static final class StubViews extends LecturerViewService {
        @Override public List<EligibilityOffering> offerings(){check(!Platform.isFxApplicationThread(),"async offerings");return List.of(new EligibilityOffering(1,"TEST","Course","A"));}
        @Override public LecturerTable load(String mode,int offering){
            check(!Platform.isFxApplicationThread(),"async records");
            return mode.equals("marks")?new LecturerTable(List.of("Enrollment","Registration","Assessment","Name","Type","Maximum","Attempt","Mark","Status"),List.of(List.of("1","TEST","2","Quiz","QUIZ","100","1","50","PRESENT"))):new LecturerTable(List.of("Name"),List.of(List.of("Test")));
        }
        @Override public void saveMark(int offering,Mark mark){check(!Platform.isFxApplicationThread(),"async marks write");check(mark.getMarksObtained()==55,"selected mark payload");saves++;}
    }
    private static final class StubMaterials extends CourseMaterialService {
        @Override public List<CourseMaterial> list(int offering){return List.of(new CourseMaterial(1,1,"Test PDF","Description","LECTURE_NOTE","test.pdf",7));}
    }
}
