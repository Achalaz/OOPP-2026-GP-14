package lk.ac.ruhuna.ftms.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.service.*;
import lk.ac.ruhuna.ftms.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit live UI writes only to a uniquely owned offering; existing academic records are read-only. */
public final class LecturerAssignmentWorkflowSmokeTest {
    private static Scene scene;
    private static Stage stage;
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (!Arrays.equals(args,new String[]{"--safe-write"})) throw new IllegalArgumentException("Pass --safe-write for a temporary live offering with cleanup.");
        CountDownLatch started = new CountDownLatch(1); Platform.startup(started::countDown);
        check(started.await(10,TimeUnit.SECONDS),"JavaFX started");
        try (var fixture = new LecturerAssignmentLiveFixture()) {
            try {
                fx(() -> {
                    Platform.setImplicitExit(false);
                    var loader = new FXMLLoader(LecturerAssignmentWorkflowSmokeTest.class.getResource("/fxml/course_offerings.fxml"));
                    loader.load();
                    for (var field : CourseOfferingController.class.getDeclaredFields()) if (field.isAnnotationPresent(FXML.class)) {
                        field.setAccessible(true); check(field.get(loader.getController())!=null,"FXML injected " + field.getName());
                    }
                    scene = new Scene(FXMLLoader.load(LecturerAssignmentWorkflowSmokeTest.class.getResource("/fxml/admin_dashboard.fxml")),1024,650);
                    stage = new Stage(); stage.setScene(scene); stage.setOpacity(0); stage.show();
                    button("Manage Course Offerings").fire(); layout();
                    check(button("Assign Lecturer").isDisabled(),"offering selection required");
                    check(combo("lecturerComboBox").getItems().stream().allMatch(l -> l instanceof LecturerCourseAssignmentDAO.LecturerOption),"profile-based lecturer choices");
                    check(combo("lecturerComboBox").getItems().size()==new LecturerAssignmentService(new LecturerCourseAssignmentDAO()).getEligibleLecturers().size(),"actual eligible lecturers loaded");
                    check(combo("assignmentTypeComboBox").getItems().equals(List.of(AssignmentType.values())),"only supported assignment types");
                    selectOffering(fixture.offering.getOfferingId());
                    check(assignments().getItems().isEmpty(),"new offering starts unassigned");
                    selectLecturer(fixture.lecturer.getUserId()); button("Assign Lecturer").fire();
                    check(status().contains("assigned successfully"),"assign action succeeds");
                    check(assignments().getItems().size()==1,"assignment table refreshes");
                    var saved = assignments().getItems().getFirst();
                    check(!saved.getLecturerName().isBlank() && !saved.getStaffNo().isBlank()
                            && !saved.getCourseCode().isBlank() && saved.getOfferingGroup().equals(fixture.offering.getOfferingGroup())
                            && !saved.getSemester().isBlank(),"all assignment display fields loaded");
                    check(new LecturerAssignmentService(new LecturerCourseAssignmentDAO()).getAssignmentsByOffering(fixture.offering.getOfferingId()).size()==1,"assignment persists on a new connection");
                    selectLecturer(fixture.lecturer.getUserId()); combo("assignmentTypeComboBox").setValue(AssignmentType.PRACTICAL);
                    button("Assign Lecturer").fire(); check(status().contains("already assigned"),"different-type duplicate rejected");
                    check(assignments().getItems().size()==1,"duplicate leaves one assignment");
                    assignments().getSelectionModel().selectFirst(); combo("assignmentTypeComboBox").setValue(AssignmentType.ASSISTANT);
                    dialogs(() -> button("Update Assignment Type").fire(),true);
                    check(assignments().getItems().getFirst().getAssignmentType()==AssignmentType.PRIMARY,"cancelled type update unchanged");
                    dialogs(() -> button("Update Assignment Type").fire(),false);
                    check(assignments().getItems().getFirst().getAssignmentType()==AssignmentType.ASSISTANT,"type update persisted and displayed");
                    button("Refresh Assignments").fire(); check(assignments().getItems().size()==1,"refresh assignments");
                    button("Clear Form").fire(); check(assignments().getItems().isEmpty() && button("Assign Lecturer").isDisabled(),"clear removes stale assignment context");
                    selectOffering(fixture.offering.getOfferingId());
                    var service = new LecturerAssignmentService(new LecturerCourseAssignmentDAO());
                    boolean checkedHistory = false;
                    for (var offering : offerings().getItems()) {
                        if (offering.getOfferingId()==fixture.offering.getOfferingId() || "CANCELLED".equals(offering.getStatus())) continue;
                        for (var assignment : service.getAssignmentsByOffering(offering.getOfferingId())) {
                            if (service.getAssignmentChangeBlocker(offering.getOfferingId(),assignment.getAssignmentId())==null) continue;
                            selectOffering(offering.getOfferingId()); assignments().getSelectionModel().selectFirst();
                            button("Remove Assignment").fire(); check(status().contains("Assignment retained"),"history blocks UI removal before confirmation");
                            check(!service.getAssignmentsByOffering(offering.getOfferingId()).isEmpty(),"historical assignment preserved");
                            checkedHistory=true; break;
                        }
                        if (checkedHistory) break;
                    }
                    check(checkedHistory,"existing historical assignment protection exercised");
                    selectOffering(fixture.offering.getOfferingId());
                    return null;
                });
                for (int[] size : new int[][]{{800,600},{1024,650},{1366,700}}) {
                    fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                    Thread.sleep(200);
                    fx(() -> {
                        ((ScrollPane)scene.getRoot()).setVvalue(1); layout();
                        for (String name : List.of("Assign Lecturer","Update Assignment Type","Remove Assignment","Refresh Assignments")) {
                            var bounds=button(name).localToScene(button(name).getBoundsInLocal());
                            check(bounds.getMinX()>=0 && bounds.getMaxX()<=scene.getWidth(),"assignment button fits " + name);
                        }
                        check(assignments().getHeight()>=180,"usable assignment table height");
                        var bottom=assignments().localToScene(assignments().getBoundsInLocal()).getMaxY();
                        check(bottom<=scene.getHeight(),"assignment table reachable by scrolling");
                        snapshot("assignments-"+size[0]+"x"+size[1]); return null;
                    });
                }
                fx(() -> {
                    var identity=SessionManager.requireSession(); double w=stage.getWidth(),h=stage.getHeight();
                    button("Back to Admin Dashboard").fire();
                    check("adminRoot".equals(scene.getRoot().getId()) && SessionManager.isCurrentSession(identity),"Back preserves exact Admin session");
                    check(stage.getScene()==scene && stage.getWidth()==w && stage.getHeight()==h,"Back keeps same window and dimensions");
                    SessionManager.startSession(fixture.lecturer);
                    NavigationUtil.navigate(scene.getRoot(),NavigationUtil.dashboardResource(),"Lecturer Dashboard");
                    check("lecturerRoot".equals(scene.getRoot().getId()),"existing lecturer dashboard loads");
                    button("Course Materials").fire(); return null;
                });
                awaitOffering("offering",fixture.offering.getOfferingId());
                check(new LecturerViewService().offerings().stream().anyMatch(o -> o.offeringId()==fixture.offering.getOfferingId()),"existing lecturer service discovers new assignment");
                fx(() -> { button("← Back to Dashboard").fire(); button("Upload Marks").fire(); return null; });
                awaitOffering("offeringCombo",fixture.offering.getOfferingId());
                check(new ResultService().getOfferings().stream().anyMatch(o -> o.offeringId()==fixture.offering.getOfferingId()),"existing ResultService sees assignment");
                for (String id : List.of("assessmentsCard","resultsCard","eligibilityCard","reportsCard","gpaCard")) {
                    fx(() -> {
                        NavigationUtil.navigate(scene.getRoot(),"/fxml/lecturer_dashboard.fxml","Lecturer Dashboard");
                        ((Button)scene.lookup("#"+id)).fire();
                        check(!"lecturerRoot".equals(scene.getRoot().getId()),"existing lecturer route opens " + id); return null;
                    });
                    awaitReady();
                }
                fx(() -> {
                    SessionManager.startSession(fixture.admin);
                    NavigationUtil.navigate(scene.getRoot(),"/fxml/admin_dashboard.fxml","Admin Dashboard");
                    button("Manage Course Offerings").fire(); layout(); selectOffering(fixture.offering.getOfferingId());
                    assignments().getSelectionModel().selectFirst();
                    dialogs(() -> button("Remove Assignment").fire(),true);
                    check(assignments().getItems().size()==1,"cancelled removal preserved assignment");
                    dialogs(() -> button("Remove Assignment").fire(),false);
                    check(assignments().getItems().isEmpty() && status().contains("Academic records were preserved"),"confirmed removal succeeds safely");
                    check(new CourseOfferingDAO().getAllOfferings().stream().anyMatch(o -> o.getOfferingId()==fixture.offering.getOfferingId()),"removal preserves offering");
                    SessionManager.startSession(fixture.lecturer);
                    check(new LecturerViewService().offerings().stream().noneMatch(o -> o.offeringId()==fixture.offering.getOfferingId()),"removed assignment no longer appears for lecturer");
                    try { new LecturerAccessService().requireOffering(fixture.offering.getOfferingId()); throw new AssertionError("Removed assignment still authorized"); }
                    catch (SecurityException expected) { checks++; }
                    return null;
                });
                System.out.println("Lecturer assignment live JavaFX workflow checks passed: " + checks + ". Credential-entry login was not exercised.");
            } finally { fx(() -> { if(stage!=null)stage.close(); return null; }); }
        } finally { Platform.exit(); }
    }

    private static void awaitOffering(String id,int offering) throws Exception {
        for (int i=0;i<150;i++) {
            if (fx(() -> combo(id).getItems().stream().anyMatch(o -> o instanceof EligibilityOffering e && e.offeringId()==offering))) {
                checks++; return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("New assignment did not appear in " + id);
    }
    private static void awaitReady() throws Exception {
        for (int i=0;i<150;i++) {
            if (fx(() -> {
                Node refresh=scene.lookup("#refreshButton");
                return refresh!=null && !refresh.isDisabled();
            })) { checks++; return; }
            Thread.sleep(100);
        }
        throw new AssertionError("Academic screen did not finish loading");
    }
    private static void selectOffering(int id) { offerings().getSelectionModel().select(offerings().getItems().stream().filter(o -> o.getOfferingId()==id).findFirst().orElseThrow()); layout(); }
    private static void selectLecturer(int id) { combo("lecturerComboBox").setValue(combo("lecturerComboBox").getItems().stream().filter(o -> o instanceof LecturerCourseAssignmentDAO.LecturerOption l && l.lecturerId()==id).findFirst().orElseThrow()); }
    @SuppressWarnings("unchecked") private static TableView<CourseOffering> offerings() { return (TableView<CourseOffering>)scene.lookup("#offeringTable"); }
    @SuppressWarnings("unchecked") private static TableView<LecturerCourseAssignment> assignments() { return (TableView<LecturerCourseAssignment>)scene.lookup("#assignedLecturerTable"); }
    @SuppressWarnings("unchecked") private static ComboBox<Object> combo(String id) { return (ComboBox<Object>)scene.lookup("#"+id); }
    private static String status() { return ((Label)scene.lookup("#assignmentStatusLabel")).getText(); }
    private static void layout() { scene.getRoot().applyCss(); scene.getRoot().layout(); }
    private static Button button(String name) { layout(); return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> b.getText().strip().equals(name)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + name)); }
    private static void dialogs(Runnable action,boolean cancel) {
        List<DialogPane> handled=new ArrayList<>();
        Timeline timer=new Timeline(new KeyFrame(Duration.millis(50), event -> {
            for(Window w:List.copyOf(Window.getWindows())) {
                if(!w.isShowing() || w.getScene()==null)continue;
                Node pane=w.getScene().getRoot().lookup(".dialog-pane");
                if(pane instanceof DialogPane dialog && !handled.contains(dialog)) {
                    handled.add(dialog);
                    check(dialog.getContentText().contains("Academic records will be preserved"),"confirmation explains access/history impact");
                    Platform.runLater(() -> ((Button)dialog.lookupButton(cancel?ButtonType.CANCEL:ButtonType.OK)).fire());
                }
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);timer.play();
        try { action.run(); } finally { timer.stop(); }
        check(handled.size()==1,"one confirmation dialog");
    }
    private static void snapshot(String name) throws Exception {
        var image=scene.getRoot().snapshot(null,null); int w=(int)image.getWidth(),h=(int)image.getHeight(); int[] pixels=new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        BufferedImage output=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);output.setRGB(0,0,w,h,pixels,0,w);
        Files.createDirectories(Path.of("target/lecturer-assignment-verification"));
        ImageIO.write(output,"png",Path.of("target/lecturer-assignment-verification",name+".png").toFile());
    }
    private static <T>T fx(Callable<T> work)throws Exception { FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(45,TimeUnit.SECONDS); }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message);checks++; }
}
