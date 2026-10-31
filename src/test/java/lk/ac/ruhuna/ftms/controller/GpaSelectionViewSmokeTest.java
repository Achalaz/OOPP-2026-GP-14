package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.*;
import lk.ac.ruhuna.ftms.service.GPAService;
import lk.ac.ruhuna.ftms.service.StudentDatabaseFixture;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;

/** Real JavaFX controls; temporary-table workflows or explicit --live SELECT-only checks. */
public final class GpaSelectionViewSmokeTest {
    private static Scene scene;
    private static int checks;
    public static void main(String[] args) throws Exception {
        var started=new CountDownLatch(1);Platform.startup(started::countDown);
        check(started.await(10,TimeUnit.SECONDS),"JavaFX started");
        try {
            if(List.of(args).contains("--live"))live();else fixture();
            System.out.println("GPA selection JavaFX checks passed: "+checks+(List.of(args).contains("--live")?"; live SELECT-only.":"; temporary tables only."));
        } finally {SessionManager.logout();Platform.exit();}
    }
    private static void fixture() throws Exception {
        try(var db=new StudentDatabaseFixture()) {
            db.publishSamples();load(new GPAService());ready();
            fx(()->{
                check(combo("studentCombo").getItems().size()==1,"fully authorized student selectable");
                check(label("accessLabel").contains("1 accessible full transcript"),"mixed access explained");
                check(details().contains("Offering 30"),"missing assignment shown without grades");
                check(!details().contains("TEST/4"),"blocked student identity omitted");
                combo("studentCombo").getSelectionModel().selectFirst();return null;
            });ready();selectSemester(9);ready();
            fx(()->{
                check(table("courseTable").getItems().size()==1,"published courses shown");
                check(label("sgpaLabel").equals("4.00")&&label("cgpaLabel").equals("4.00"),"weighted GPA shown");
                check(!button("calculateButton").isDisabled(),"Calculate enabled");
                button("calculateButton").fire();return null;
            });ready();fx(()->{button("saveButton").fire();return null;});ready();
            check(((Number)db.academic.scalar("SELECT sgpa FROM semester_results WHERE student_id=3 AND semester_id=9")).doubleValue()==4.0,"Save persists recalculated GPA");
            fx(()->{button("refreshButton").fire();return null;});ready();
            fx(()->{
                check(((StudentOption)combo("studentCombo").getValue()).studentId()==3,"Refresh retains authorized student");
                check(((SemesterOption)combo("semesterCombo").getValue()).semesterId()==9,"Refresh retains semester");
                check(label("savedStateLabel").contains("Saved and current"),"Refresh reloads saved GPA");
                snapshot("gpa-selection-authorized");button("reportsButton").fire();return null;
            });ready();
            fx(()->{check(combo("offeringCombo").getItems().size()==2,"existing scoped reports open");return null;});

            db.academic.login(2,"LECTURER");load(new GPAService());ready();
            fx(()->{
                check(combo("studentCombo").getItems().isEmpty(),"partial lecturer cannot select full transcript");
                check(combo("semesterCombo").isDisabled()&&button("calculateButton").isDisabled(),"partial GPA controls disabled");
                check(details().contains("Offering 10")&&details().contains("TEST-A"),"partial missing offering identified");
                check(table("courseTable").getItems().isEmpty(),"partial lecturer receives no unassigned grades");
                snapshot("gpa-selection-partial");button("reportsButton").fire();return null;
            });ready();
            fx(()->{check(combo("offeringCombo").getItems().size()==1,"partial lecturer has scoped course reports");return null;});
            load(new GPAService());ready();
            db.academic.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=2");
            fx(()->{button("refreshButton").fire();return null;});ready();
            fx(()->{check(label("accessLabel").startsWith("No lecturer course assignments"),"no assignments explained");return null;});
            db.academic.execute("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(10,2,'ASSISTANT'),(30,2,'PRIMARY')");
            fx(()->{button("refreshButton").fire();return null;});ready();
            fx(()->{
                check(combo("studentCombo").getItems().size()==1,"Refresh sees legitimate assignment change");
                combo("studentCombo").getSelectionModel().selectFirst();return null;
            });ready();selectSemester(9);ready();
            db.academic.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=2 AND offering_id=10");
            fx(()->{button("refreshButton").fire();return null;});ready();
            fx(()->{
                check(combo("studentCombo").getItems().isEmpty()&&table("courseTable").getItems().isEmpty(),"revocation clears selection and grades");
                check(button("calculateButton").isDisabled()&&button("saveButton").isDisabled(),"revocation disables GPA actions");return null;
            });

            db.academic.login(3,"STUDENT");load(new GPAService());ready();
            fx(()->{
                check(combo("studentCombo").isDisabled()&&combo("studentCombo").getItems().size()==1,"student can select only self");
                check(!button("saveButton").isVisible()&&!button("calculateButton").isVisible(),"student GPA is read only");
                check(table("courseTable").getItems().isEmpty(),"draft-only semester hides grades");
                check(label("statusLabel").contains("No published course results"),"no publication explained");return null;
            });selectSemester(9);ready();
            fx(()->{check(label("sgpaLabel").equals("4.00"),"student published GPA visible");return null;});
            db.academic.execute("UPDATE enrollments SET attempt_no=2 WHERE enrollment_id=100");
            fx(()->{button("refreshButton").fire();return null;});ready();
            fx(()->{
                check(label("sgpaLabel").equals("Pending")&&label("cgpaLabel").equals("Pending"),"repeat policy remains Pending");
                check(label("statusLabel").contains("Repeat replacement policy"),"repeat reason displayed");return null;
            });
            for(String state:List.of("08001","42S02")) {
                load(new GPAService(){@Override public StudentSelection getStudentSelection() throws SQLException {throw new SQLException("Fixture failure",state);}});
                ready();fx(()->{
                    check(label("statusLabel").startsWith(state.equals("08001")?"Database connection failed":"Database query failed"),"database error distinguished");
                    check(combo("studentCombo").getItems().isEmpty()&&table("courseTable").getItems().isEmpty(),"failed read exposes no stale data");return null;
                });
            }
        }
    }
    private static void live() throws Exception {
        try(var c=lk.ac.ruhuna.ftms.database.DBConnection.getConnection();var p=c.prepareStatement(
                "SELECT user_id,username FROM users WHERE role='LECTURER' AND is_active=TRUE ORDER BY user_id");var accounts=p.executeQuery()) {
            int count=0;
            while(accounts.next()) {
                String username=accounts.getString(2);
                SessionManager.startSession(new lk.ac.ruhuna.ftms.model.User(accounts.getInt(1),username,"Live verification",null,"LECTURER",true));
                load(new GPAService());ready();
                fx(()->{
                    check(combo("studentCombo").getItems().isEmpty(),"live full transcripts require missing assignments");
                    check(table("courseTable").getItems().isEmpty(),"live restricted grades withheld");
                    check(label("statusLabel").contains("No accessible full transcripts")||label("statusLabel").contains("lecturer profile is missing"),"live cause explained");
                    if(username.equals("lecturer2")) {
                        check(details().contains("Offering 1 | ICT2113")&&details().contains("Offering 5 | ICT2142"),"actual missing assignments identified");
                        snapshot("gpa-selection-live-lecturer2");
                    }
                    System.out.println("LIVE "+username+": "+label("statusLabel"));return null;
                });count++;
            }
            check(count>0,"existing lecturer accounts checked");
        }
    }
    private static void load(GPAService service) throws Exception {
        fx(()->{
            var loader=new FXMLLoader(GpaSelectionViewSmokeTest.class.getResource("/fxml/gpa.fxml"));
            loader.setControllerFactory(type->new GPAController(service));Parent root=loader.load();
            if(scene==null)scene=new Scene(root,1280,900);else scene.setRoot(root);
            root.applyCss();root.layout();return null;
        });
    }
    private static void selectSemester(int id)throws Exception {
        fx(()->{combo("semesterCombo").getItems().stream().filter(s->((SemesterOption)s).semesterId()==id).findFirst()
                .ifPresent(s->combo("semesterCombo").getSelectionModel().select(s));return null;});
    }
    private static void ready()throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<limit){if(fx(()->!button("refreshButton").isDisabled()))return;Thread.sleep(25);}
        throw new AssertionError("GPA screen did not finish loading");
    }
    private static javafx.scene.Node node(String id){return scene.getRoot().lookup("#"+id);}
    private static Button button(String id){return (Button)node(id);}
    @SuppressWarnings("unchecked") private static ComboBox<Object> combo(String id){return (ComboBox<Object>)node(id);}
    private static TableView<?> table(String id){return (TableView<?>)node(id);}
    private static String label(String id){return ((Label)node(id)).getText();}
    private static String details(){return ((TextArea)node("assignmentDetails")).getText();}
    private static <T>T fx(Callable<T> work)throws Exception{var task=new FutureTask<>(work);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static void snapshot(String name)throws Exception{
        scene.getRoot().applyCss();scene.getRoot().layout();var image=scene.getRoot().snapshot(null,null);
        var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(output,"png",new java.io.File("target/"+name+".png"));
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
