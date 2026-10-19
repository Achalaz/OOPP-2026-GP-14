package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.*;
import java.util.concurrent.*;

/** Real FXML/controller/SQL workflow with only connection-local fixture writes and no visible window. */
public final class AcademicViewsSmokeTest {
    private static Scene scene;private static int checks;
    public static void main(String[] args)throws Exception{
        try(var db=new AcademicDatabaseFixture()){
            db.login(1,"LECTURER");
            new MarkService().saveMark(new Mark(100,10,40.0,1));new MarkService().saveMark(new Mark(100,11,80.0,1));
            db.confirmedFixtureEligibility(100);var results=new ResultService();var draft=results.calculateAndSaveResult(100);
            results.publishResult(10,draft.publicationSnapshot());
            CountDownLatch started=new CountDownLatch(1);Platform.startup(started::countDown);
            check(started.await(10,TimeUnit.SECONDS),"JavaFX started");
            try{
                var identity=SessionManager.requireSession();
                open("assessments");ready();
                fx(()->{combo("offeringCombo").getSelectionModel().select(1);return null;});ready();
                fx(()->{
                    field("nameField").setText("UI Practice");field("maxField").setText("20");field("weightField").setText("0");
                    button("saveButton").fire();return null;
                });ready();
                fx(()->{check(table("assessmentTable").getItems().size()==2,"assessment save refreshes table");return null;});
                open("eligibility");ready();
                fx(()->{combo("offeringCombo").getSelectionModel().selectFirst();return null;});ready();
                fx(()->{combo("enrollmentCombo").getSelectionModel().select(1);button("calculateButton").fire();return null;});ready();
                fx(()->{
                    check(label("overallEligibilityLabel").getText().equals("Pending"),"overall eligibility pending");
                    check(label("attendancePercentageLabel").getText().equals("0.00%"),"actual zero attendance shown");
                    button("saveButton").fire();return null;
                });ready();
                check(new lk.ac.ruhuna.ftms.dao.EligibilityDAO().getByEnrollmentId(101).isOverallEligible()==null,"pending persists as SQL NULL");
                open("results");ready();
                fx(()->{combo("offeringCombo").getSelectionModel().selectFirst();return null;});ready();
                fx(()->{
                    combo("enrollmentCombo").getSelectionModel().selectFirst();
                    check(label("resultStateLabel").getText().contains("Published"),"published status shown");
                    check(button("publishButton").isDisabled(),"duplicate publication disabled");
                    return null;
                });
                open("gpa");ready();
                fx(()->{combo("studentCombo").getSelectionModel().selectFirst();return null;});ready();
                fx(()->{combo("semesterCombo").getSelectionModel().selectFirst();return null;});ready();
                fx(()->{check(label("sgpaLabel").getText().equals("4.00"),"GPA loads current publication");return null;});
                open("reports");ready();
                fx(()->{combo("batchCombo").getSelectionModel().selectFirst();button("batchReportButton").fire();return null;});ready();
                fx(()->{
                    check(table("reportTable").getItems().size()==2,"batch report loaded");
                    check(label("passLabel").getText().equals("1"),"published pass count");
                    check(label("pendingLabel").getText().equals("1"),"pending report count");
                    table("reportTable").getSelectionModel().selectFirst();return null;
                });ready();
                fx(()->{
                    check(!((TextArea)scene.getRoot().lookup("#detailsArea")).getText().isBlank(),"assessment details loaded");
                    button("dashboardButton").fire();
                    check("lecturerRoot".equals(scene.getRoot().getId()),"back returns to lecturer dashboard");
                    check(SessionManager.isCurrentSession(identity),"navigation preserves exact session");return null;
                });
                // Exercise every dashboard entry, including both the card and sidebar routes.
                for(String suffix:List.of("Nav","Card")){
                    for(var entry:Map.of("assessments","assessmentTable","marks","marksTable",
                            "eligibility","overallEligibilityLabel","results","resultStateLabel",
                            "gpa","sgpaLabel","reports","reportTable").entrySet()){
                        System.out.println("Dashboard route: "+entry.getKey()+suffix);
                        fx(()->{scene.getRoot().applyCss();scene.getRoot().layout();button(entry.getKey()+suffix).fire();return null;});ready();
                        fx(()->{
                            check(scene.getRoot().lookup("#"+entry.getValue())!=null,entry.getKey()+suffix+" opens the correct academic screen");
                            button("dashboardButton").fire();
                            check("lecturerRoot".equals(scene.getRoot().getId()),entry.getKey()+suffix+" returns to lecturer dashboard");
                            check(SessionManager.isCurrentSession(identity),entry.getKey()+suffix+" preserves session");return null;
                        });
                    }
                }
                if(Arrays.asList(args).contains("--screenshots"))snapshot("lecturer-dashboard");
                db.login(3,"STUDENT");open("student_dashboard");
                var studentIdentity=SessionManager.requireSession();
                for(String suffix:List.of("Nav","Card")){
                    fx(()->{button("studentResults"+suffix).fire();return null;});ready();
                    fx(()->{
                        check(scene.getRoot().lookup("#studentCombo")==null,"student cannot select another identity");
                        check(table("recordsTable").getItems().size()==1,"only published student results load automatically");
                        check(!table("recordsTable").isEditable(),"student results are read only");
                        button("dashboardButton").fire();
                        check("studentRoot".equals(scene.getRoot().getId()),"results return to student dashboard");
                        scene.getRoot().applyCss();scene.getRoot().layout();
                        button("studentGpa"+suffix).fire();return null;
                    });ready();
                    fx(()->{
                        check(table("summaryTable").getItems().size()==1,"student GPA shows published semesters only");
                        check(scene.getRoot().lookup("#saveButton")==null,"student GPA is read only");
                        button("dashboardButton").fire();
                        check("studentRoot".equals(scene.getRoot().getId()),"GPA returns to student dashboard");
                        check(SessionManager.isCurrentSession(studentIdentity),"student navigation preserves session");return null;
                    });
                }
                if(Arrays.asList(args).contains("--screenshots"))snapshot("student-dashboard");
                System.out.println("Academic JavaFX workflow checks passed: "+checks);
            }finally{Platform.exit();}
        }
    }
    private static void open(String name)throws Exception{
        fx(()->{var root=FXMLLoader.<javafx.scene.Parent>load(AcademicViewsSmokeTest.class.getResource("/fxml/"+name+".fxml"));
            if(scene==null)scene=new Scene(root,1280,900);else scene.setRoot(root);root.applyCss();root.layout();return null;});
    }
    private static void snapshot(String name)throws Exception{
        fx(()->{
            scene.getRoot().applyCss();scene.getRoot().layout();
            var image=scene.getRoot().snapshot(null,null);
            var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var pixels=image.getPixelReader();
            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,pixels.getArgb(x,y));
            javax.imageio.ImageIO.write(output,"png",new java.io.File("target/"+name+".png"));return null;
        });
    }
    private static void ready()throws Exception{
        for(int i=0;i<300;i++){
            if(fx(()->{
                scene.getRoot().applyCss();scene.getRoot().layout();
                var node=scene.getRoot().lookup("#refreshButton");
                return node!=null ? !node.isDisabled() : scene.getRoot().lookup("#marksTable")!=null;
            }))return;
            Thread.sleep(30);
        }throw new AssertionError("Academic screen did not finish loading");
    }
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static Button button(String id){
        scene.getRoot().applyCss();scene.getRoot().layout();
        return (Button)scene.getRoot().lookup("#"+id);
    }
    private static TextField field(String id){return (TextField)scene.getRoot().lookup("#"+id);}
    private static Label label(String id){return (Label)scene.getRoot().lookup("#"+id);}
    private static ComboBox<?> combo(String id){return (ComboBox<?>)scene.getRoot().lookup("#"+id);}
    private static TableView<?> table(String id){return (TableView<?>)scene.getRoot().lookup("#"+id);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
