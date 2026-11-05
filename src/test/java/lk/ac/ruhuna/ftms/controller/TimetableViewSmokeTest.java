package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.AssignedLecturer;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.model.AttendanceOffering;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.service.TimetableDatabaseFixture;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Explicit JavaFX runner. Live accounts are read only; CRUD uses temporary MySQL fixture tables. */
public final class TimetableViewSmokeTest {
    private static Stage stage;
    private static Object controller;
    private static Parent root;
    private static int checks;
    private static Timeline dialogs;
    private static boolean confirmChanges = true;
    private static final java.util.ArrayList<String> errors = new java.util.ArrayList<>();
    private static final java.util.Map<Window,Long> dialogSeen = new java.util.IdentityHashMap<>();

    public static void main(String[] args) throws Exception {
        String before = TimetableDatabaseFixture.productionFingerprint();
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timeout");
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                stage = new Stage(); stage.setOpacity(0);
                stage.setScene(new Scene(new BorderPane(), 1000, 650)); stage.show();
                dialogs = new Timeline(new KeyFrame(Duration.millis(40), e -> Platform.runLater(TimetableViewSmokeTest::dismissDialogs)));
                dialogs.setCycleCount(Timeline.INDEFINITE); dialogs.play();
                return null;
            });
            // Exercise actual Dashboard handlers and NavigationUtil with existing live accounts. No live writes.
            var users = new UserDAO().getAllUsers();
            for (String role : List.of("ADMIN", "STUDENT", "TECHNICAL_OFFICER")) {
                var user = users.stream().filter(u -> u.isActive() && role.equals(u.getRole())).findFirst().orElseThrow();
                String dashboard = switch(role) {
                    case "ADMIN" -> "admin_dashboard";
                    case "STUDENT" -> "student_dashboard";
                    default -> "technical_officer_dashboard";
                };
                fx(() -> {
                    SessionManager.startSession(user);
                    var loader = new FXMLLoader(TimetableViewSmokeTest.class.getResource("/fxml/"+dashboard+".fxml"));
                    root = loader.load(); stage.getScene().setRoot(root);
                    double width=stage.getWidth(), height=stage.getHeight();
                    invoke(loader.getController(),role.equals("ADMIN") ? "handleTimetables" : "handleTimetable");
                    root=stage.getScene().getRoot();
                    check(root.getId().equals(switch(role) {
                        case "ADMIN" -> "adminTimetableRoot";
                        case "STUDENT" -> "studentTimetableRoot";
                        default -> "timetableRoot";
                    }), role+" dashboard opens timetable");
                    check(Math.abs(stage.getWidth()-width)<2 && Math.abs(stage.getHeight()-height)<2, role+" preserves window size");
                    return null;
                });
                awaitIdle();
                for (int[] size : List.of(new int[]{1280,720},new int[]{1000,650},new int[]{760,540},new int[]{640,480})) {
                    fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                    Thread.sleep(150); // Allow the native window resize and JavaFX pulse to update Scene dimensions.
                    fx(() -> {
                        root.applyCss(); root.layout();
                        var button=(Button)root.lookup("#backButton");
                        var bounds=button.localToScene(button.getBoundsInLocal());
                        check(bounds.getMinX()>=0 && bounds.getMaxX()<=stage.getScene().getWidth()+1
                                && bounds.getMinY()>=0 && bounds.getMaxY()<=stage.getScene().getHeight()+1, role+" back button accessible "+size[0]);
                        check(root.lookupAll(".scroll-pane").stream().anyMatch(n -> n instanceof ScrollPane), role+" content scrolls");
                        check(stage.getStyle()==javafx.stage.StageStyle.DECORATED && !stage.isFullScreen(),"native title bar retained");
                        if(size[0]==760) snapshot(role.toLowerCase()+"-timetable");
                        return null;
                    });
                }
                fx(() -> {
                    var bounds=javafx.stage.Screen.getPrimary().getVisualBounds();
                    stage.setX(bounds.getMinX()-100);stage.setY(bounds.getMinY()-100);
                    stage.setWidth(bounds.getWidth()+200);stage.setHeight(bounds.getHeight()+200);
                    String resource=switch(role) {
                        case "ADMIN" -> "/fxml/admin_timetable.fxml";
                        case "STUDENT" -> "/fxml/student_timetable.fxml";
                        default -> "/fxml/timetable.fxml";
                    };
                    NavigationUtil.navigate(root,resource,"FTMS timetable");root=stage.getScene().getRoot();
                    check(stage.getX()>=bounds.getMinX() && stage.getY()>=bounds.getMinY()
                            && stage.getX()+stage.getWidth()<=bounds.getMaxX()+1
                            && stage.getY()+stage.getHeight()<=bounds.getMaxY()+1,"oversized timetable clamped inside display");
                    return null;
                });
                awaitIdle();
                fx(() -> {
                    var loginIdentity = SessionManager.requireSession();
                    ((Button)root.lookup("#backButton")).fire(); root=stage.getScene().getRoot();
                    String dashboardRoot = switch(role) {
                        case "ADMIN" -> "adminRoot";
                        case "STUDENT" -> "studentRoot";
                        default -> "technicalOfficerRoot";
                    };
                    check(dashboardRoot.equals(root.getId()), role+" back opens the correct dashboard");
                    check(SessionManager.isCurrentSession(loginIdentity), role+" back preserves the authenticated session");
                    var loader=new FXMLLoader(TimetableViewSmokeTest.class.getResource("/fxml/"+dashboard+".fxml"));
                    root=loader.load(); stage.getScene().setRoot(root);
                    invoke(loader.getController(),"handleLogout");
                    check(!SessionManager.isLoggedIn(),role+" logout clears session"); return null; });
            }

            try (var f = new TimetableDatabaseFixture()) {
                fx(() -> {
                    TimetableDatabaseFixture.login(1,"ADMIN");
                    load("admin_timetable");
                    injectService(f.admin());
                    ((Button)root.lookup("#refreshButton")).fire(); return null;
                });
                awaitIdle();
                fx(() -> {
                    check(table().getItems().isEmpty(),"empty admin table");
                    check(((Label)root.lookup("#statusLabel")).getText().contains("Use the entry form"),"empty admin timetable explains how to add a session");
                    var offerings=combo("entryOfferingComboBox");
                    check(offerings.getItems().size()==4,"schedulable combo excludes cancelled");
                    offerings.setValue(offerings.getItems().stream().filter(o -> ((AttendanceOffering)o).offeringId()==10).findFirst().orElseThrow());
                    var lecturers=combo("lecturerComboBox"); check(lecturers.getItems().size()==2,"assigned lecturer and Unassigned choices");
                    lecturers.setValue(lecturers.getItems().get(1));
                    combo("dayComboBox").setValue("MONDAY");
                    ((TextField)root.lookup("#startTimeField")).setText("09:00");
                    ((TextField)root.lookup("#endTimeField")).setText("10:00");
                    ((TextField)root.lookup("#roomField")).setText("UI-ROOM");
                    var date=(DatePicker)root.lookup("#effectiveFromPicker");
                    date.getEditor().setText("2026-02-30");
                    try { invoke(controller,"readInput"); throw new AssertionError("Invalid calendar date accepted"); }
                    catch(java.lang.reflect.InvocationTargetException expected) {
                        check(expected.getCause() instanceof IllegalArgumentException,"invalid typed date rejected");
                    }
                    date.getEditor().clear();
                    ((Button)root.lookup("#addButton")).fire(); return null;
                });
                awaitRows(1);
                fx(() -> {
                    table().getSelectionModel().select(0);
                    check(((TextField)root.lookup("#roomField")).getText().equals("UI-ROOM"),"row populates form");
                    check(((AssignedLecturer)combo("lecturerComboBox").getValue()).lecturerId()==4,"row selects assigned lecturer");
                    ((TextField)root.lookup("#roomField")).setText("UI-UPDATED");
                    ((Button)root.lookup("#updateButton")).fire(); return null;
                });
                awaitIdle();
                fx(() -> { check(((TimetableEntry)table().getItems().getFirst()).room().equals("UI-UPDATED"),"update refreshes table");
                    table().getSelectionModel().select(0); confirmChanges=false;
                    ((Button)root.lookup("#deleteButton")).fire(); check(table().getItems().size()==1,"delete confirmation No preserves row");
                    check(((Label)root.lookup("#statusLabel")).getText().contains("Deletion cancelled"),"cancelled deletion is explained");
                    confirmChanges=true; ((Button)root.lookup("#deleteButton")).fire(); return null; });
                awaitRows(0);
                check(f.count()==0,"UI create/update/delete persisted in temporary MySQL tables");
                f.execute("INSERT INTO timetable_entries (offering_id,day_of_week,start_time,end_time) VALUES (10,'MONDAY','09:00','10:00'),(11,'TUESDAY','11:00','12:00')");
                fx(() -> { ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitRows(2);
                fx(() -> { var filter=combo("filterOfferingComboBox"); filter.setValue(filter.getItems().stream()
                            .filter(o -> ((AttendanceOffering)o).offeringId()==10).findFirst().orElseThrow()); return null; }); awaitRows(1);
                fx(() -> { ((Button)root.lookup("#allCoursesButton")).fire(); return null; }); awaitRows(2);
                fx(() -> { table().getSelectionModel().select(0); ((Button)root.lookup("#clearButton")).fire();
                    check(table().getSelectionModel().getSelectedItem()==null && ((TextField)root.lookup("#startTimeField")).getText().isEmpty(),"clear resets selection and form"); return null; });
                f.execute("INSERT INTO course_offerings (offering_id,course_id,semester_id,status) VALUES (14,1,5,'ACTIVE')");
                fx(() -> { ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitIdle();
                fx(() -> { check(combo("filterOfferingComboBox").getItems().size()==6,"refresh reloads new offerings"); return null; });
                controllerReadFailureChecks(f, "ADMIN", "filterOfferingComboBox");
                adminWriteFailureChecks(f);
                fx(() -> {
                    TimetableDatabaseFixture.login(3,"STUDENT"); load("student_timetable"); injectService(f.student());
                    ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitRows(2);
                fx(() -> {
                    check(!table().isEditable(), "student timetable is read-only");
                    table().edit(0, table().getColumns().getFirst());
                    check(table().getEditingCell()==null, "student cannot start editing a timetable cell");
                    check(List.of("#addButton", "#updateButton", "#deleteButton", "#entryOfferingComboBox").stream()
                            .allMatch(id -> root.lookup(id)==null), "student timetable has no management controls");
                    combo("dayFilterComboBox").setValue("MONDAY"); check(table().getItems().size()==1,"student day filter");
                    combo("dayFilterComboBox").setValue("SUNDAY"); check(table().getItems().isEmpty(),"empty day filter");
                    check(((Label)root.lookup("#statusLabel")).getText().contains("No sessions are scheduled for Sunday. Select All Days"),"empty day message names day and next action");
                    combo("dayFilterComboBox").setValue("All Days"); return null; });
                studentPrivacyChecks(f);
                controllerReadFailureChecks(f, "STUDENT", null);
                f.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE student_id=3");
                fx(() -> { ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitRows(0);
                fx(() -> { check(((Label)root.lookup("#statusLabel")).getText().contains("no enrolled or repeat course offerings"),"missing enrollment message"); return null; });
                f.execute("UPDATE enrollments SET status='ENROLLED' WHERE student_id=3 AND offering_id=13");
                fx(() -> { ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitRows(0);
                fx(() -> { check(((Label)root.lookup("#statusLabel")).getText().contains("No timetable entries are available for your enrolled"),"missing schedule is distinct from missing enrollment"); return null; });
                fx(() -> { TimetableDatabaseFixture.login(2,"TECHNICAL_OFFICER"); load("timetable"); injectService(f.officer());
                    ((Button)root.lookup("#refreshButton")).fire(); return null; }); awaitRows(2);
                fx(() -> { check(combo("offeringComboBox").getItems().size()==5,"officer historical and refreshed dropdown"); return null; });
                controllerReadFailureChecks(f, "TECHNICAL_OFFICER", "offeringComboBox");
                lifecycleChecks();
            }
            check(before.equals(TimetableDatabaseFixture.productionFingerprint()),"production data unchanged after UI tests");
            check(errors.isEmpty(),"unexpected dialogs: "+errors);
            System.out.println("Timetable JavaFX: "+checks+" checks passed; production data unchanged.");
        } finally {
            fx(() -> { if(dialogs!=null)dialogs.stop(); for(var window:List.copyOf(Window.getWindows()))window.hide(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void controllerReadFailureChecks(TimetableDatabaseFixture f, String role, String courseFilter) throws Exception {
        String cause = "fixture " + role + " read failure";
        var identity = SessionManager.requireSession();
        int previousErrors = fx(errors::size);
        f.failNextConnection(new java.sql.SQLException(cause, "08001"));
        fx(() -> {
            table().getSelectionModel().select(0);
            if (courseFilter == null) ((Button)root.lookup("#refreshButton")).fire();
            else {
                var filter = combo(courseFilter);
                filter.setValue(filter.getItems().stream().filter(value -> ((AttendanceOffering)value).offeringId()==10).findFirst().orElseThrow());
            }
            return null;
        });
        awaitIdle();
        fx(() -> {
            check(errors.size()==previousErrors+1 && errors.removeLast().contains(cause), role+" failure dialog shows the database root cause");
            check(table().getItems().isEmpty(), role+" failed read clears stale timetable rows");
            check(!((Label)root.lookup("#summaryLabel")).getText().startsWith("Loading"), role+" failed read clears loading summary");
            check(!((Button)root.lookup("#refreshButton")).isDisabled() && !((Button)root.lookup("#backButton")).isDisabled(), role+" failed read leaves Refresh and Back usable");
            check(SessionManager.isCurrentSession(identity), role+" read failure retains the authenticated session");
            if (role.equals("ADMIN")) check(((Button)root.lookup("#updateButton")).isDisabled()
                    && ((Button)root.lookup("#deleteButton")).isDisabled(), "failed Admin read removes the stale edit selection");
            ((Button)root.lookup("#refreshButton")).fire(); return null;
        });
        awaitRows(2);
    }

    private static void adminWriteFailureChecks(TimetableDatabaseFixture f) throws Exception {
        var savedRows = f.admin().getAllTimetable();
        int previousErrors = fx(errors::size);
        f.failNextConnection(new java.sql.SQLException("fixture Admin save failure", "08001"));
        fx(() -> {
            table().getSelectionModel().select(0);
            ((TextField)root.lookup("#roomField")).setText("UNSAVED-DRAFT");
            ((Button)root.lookup("#updateButton")).fire(); return null;
        });
        awaitIdle();
        fx(() -> {
            check(errors.size()==previousErrors+1 && errors.removeLast().contains("fixture Admin save failure"), "Admin save failure exposes the root cause");
            check(((TextField)root.lookup("#roomField")).getText().equals("UNSAVED-DRAFT"), "failed save preserves the form for retry");
            check(!((Button)root.lookup("#updateButton")).isDisabled() && table().getSelectionModel().getSelectedItem()!=null,
                    "failed save preserves the selected entry and re-enables Update");
            ((Button)root.lookup("#clearButton")).fire(); return null;
        });
        check(f.admin().getAllTimetable().equals(savedRows), "failed save leaves stored timetable entries unchanged");
    }

    private static void studentPrivacyChecks(TimetableDatabaseFixture f) throws Exception {
        // Keep the old screen attached to exercise its cached day filter after another login.
        fx(() -> {
            TimetableDatabaseFixture.login(6,"STUDENT");
            combo("dayFilterComboBox").setValue("MONDAY");
            check(table().getItems().isEmpty(), "changed student session clears cached timetable rows");
            check(((Label)root.lookup("#studentNameLabel")).getText().equals("My Timetable"),
                    "changed student session removes the previous student's name");
            check(((Label)root.lookup("#statusLabel")).getText().contains("sign in again"),
                    "expired timetable explains how to regain access");
            return null;
        });
        f.execute("INSERT INTO timetable_entries (offering_id,day_of_week,start_time,end_time) VALUES (20,'SATURDAY','12:00','13:00')");
        fx(() -> {
            load("student_timetable"); injectService(f.student());
            ((Button)root.lookup("#refreshButton")).fire(); return null;
        });
        awaitRows(1);
        fx(() -> {
            check(((TimetableEntry)table().getItems().getFirst()).offeringId()==20,
                    "second student's screen shows only their own enrolled offering");
            Parent oldRoot = root;
            TimetableDatabaseFixture.login(7,"STUDENT"); load("student_timetable"); injectService(f.student());
            check(((TableView<?>)oldRoot.lookup("#timetableTable")).getItems().isEmpty(),
                    "leaving a student timetable clears its cached rows");
            check(((Label)oldRoot.lookup("#studentNameLabel")).getText().equals("My Timetable"),
                    "leaving a student timetable clears its student's name");
            ((Button)root.lookup("#refreshButton")).fire(); return null;
        });
        awaitRows(0);
        fx(() -> {
            check(((Label)root.lookup("#statusLabel")).getText().contains("no enrolled or repeat"),
                    "student without enrollments never inherits another student's timetable");
            TimetableDatabaseFixture.login(3,"STUDENT"); load("student_timetable"); injectService(f.student());
            ((Button)root.lookup("#refreshButton")).fire(); return null;
        });
        awaitRows(2);
    }

    private static void lifecycleChecks() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        var callbacks=new java.util.concurrent.atomic.AtomicInteger();
        var clears=new java.util.concurrent.atomic.AtomicInteger();
        var busy=new SimpleBooleanProperty();
        fx(() -> { var runner=(TimetableTaskRunner)field(controller,"tasks"); runner.dispose();
            var taskRoot=new BorderPane(); stage.getScene().setRoot(taskRoot);
            var tasks=new TimetableTaskRunner(taskRoot,busy,new Label(),SessionManager.requireSession(),clears::incrementAndGet);
            tasks.run("Loading",() -> { started.countDown(); release.await(5,TimeUnit.SECONDS); return 1; },v -> callbacks.incrementAndGet(),e -> callbacks.incrementAndGet());
            return null; });
        check(started.await(5,TimeUnit.SECONDS),"background task starts");
        fx(() -> { stage.getScene().setRoot(new BorderPane()); check(!busy.get(),"navigation cancels and re-enables controls"); return null; });
        release.countDown(); Thread.sleep(150);
        fx(() -> { check(callbacks.get()==0,"detached callbacks discarded");
            check(clears.get()==1,"leaving the screen clears cached data once"); return null; });
        var done=new CountDownLatch(1);
        fx(() -> { var taskRoot=new BorderPane();stage.getScene().setRoot(taskRoot);var status=new Label();
            var tasks=new TimetableTaskRunner(taskRoot,busy,status,SessionManager.requireSession(),() -> {});
            tasks.run("Loading",() -> { throw new IllegalStateException("wrapper",new java.sql.SQLException("fixture root cause")); },v -> {},e -> {
                check(Platform.isFxApplicationThread() && !busy.get(),"failure on FX thread re-enables controls");
                status.setText(TimetableTaskRunner.errorMessage("Failed",e)); check(status.getText().contains("fixture root cause"),"root cause displayed"); done.countDown(); });
            return null; });
        check(done.await(5,TimeUnit.SECONDS),"failure callback completes");
        for(String mode:List.of("cancel","timeout","logout","switch","close")) {
            var began=new CountDownLatch(1); var unblock=new CountDownLatch(1); var finished=new CountDownLatch(1);
            var stale=new java.util.concurrent.atomic.AtomicInteger();
            var ref=new java.util.concurrent.atomic.AtomicReference<TimetableTaskRunner>();
            var status=new java.util.concurrent.atomic.AtomicReference<Label>();
            fx(() -> {
                TimetableDatabaseFixture.login(mode.equals("switch") ? 3 : 2,
                        mode.equals("switch") ? "STUDENT" : "TECHNICAL_OFFICER");
                var taskRoot=new BorderPane(); stage.getScene().setRoot(taskRoot);
                status.set(new Label());
                var runner=new TimetableTaskRunner(taskRoot,busy,status.get(),SessionManager.requireSession(),finished::countDown);
                ref.set(runner);
                if(mode.equals("timeout")) ((javafx.animation.PauseTransition)field(runner,"timeout")).setDuration(Duration.millis(100));
                runner.run("Loading",() -> {began.countDown();unblock.await(5,TimeUnit.SECONDS);return 1;},v -> stale.incrementAndGet(),e -> {
                    check(mode.equals("timeout") && !busy.get(),"timeout re-enables controls"); finished.countDown();
                }); return null;
            });
            check(began.await(5,TimeUnit.SECONDS),mode+" starts off UI thread");
            fx(() -> {
                switch(mode) {
                    case "cancel" -> ((javafx.concurrent.Task<?>)field(ref.get(),"active")).cancel(true);
                    case "logout" -> {SessionManager.logout();unblock.countDown();}
                    case "switch" -> {TimetableDatabaseFixture.login(6,"STUDENT");unblock.countDown();}
                    case "close" -> {stage.hide();finished.countDown();}
                }
                return null;
            });
            check(finished.await(5,TimeUnit.SECONDS),mode+" completes safely"); unblock.countDown();
            fx(() -> {
                check(!busy.get() && stale.get()==0,mode+" discards stale success");
                if(!mode.equals("timeout"))check(!status.get().getText().equals("Loading"),mode+" clears loading status");
                ref.get().dispose(); return null;
            });
        }
    }
    private static void snapshot(String name) throws Exception {
        var image=root.snapshot(null,null);int width=(int)image.getWidth(),height=(int)image.getHeight();
        int[] pixels=new int[width*height];image.getPixelReader().getPixels(0,0,width,height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        var output=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0,0,width,height,pixels,0,width);
        var directory=java.nio.file.Path.of("target","timetable-verification");java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output,"png",directory.resolve(name+".png").toFile());
    }
    private static void load(String name) throws Exception {
        var loader=new FXMLLoader(TimetableViewSmokeTest.class.getResource("/fxml/"+name+".fxml"));
        root=loader.load(); controller=loader.getController(); stage.getScene().setRoot(root);
        root.applyCss(); root.layout();
        for(var field:controller.getClass().getDeclaredFields())if(field.isAnnotationPresent(FXML.class)) {
            field.setAccessible(true);check(field.get(controller)!=null,"FXML injects "+field.getName());
        }
    }
    private static void injectService(Object service) throws Exception {
        ((TimetableTaskRunner)field(controller,"tasks")).dispose();
        setField(controller,"timetableService",service);
        var busy=(SimpleBooleanProperty)field(controller,"busy");
        Object loadedController = controller;
        setField(controller,"tasks",new TimetableTaskRunner(root,busy,(Label)root.lookup("#statusLabel"),SessionManager.requireSession(),() -> {
            try { invoke(loadedController,"clearData"); }
            catch (Exception error) { throw new IllegalStateException("Could not clear the test controller's timetable", error); }
        }));
    }
    private static void dismissDialogs() {
        for(var window:List.copyOf(Window.getWindows()))if(window!=stage && window.getScene()!=null) {
            long seen=dialogSeen.computeIfAbsent(window,ignored -> System.nanoTime());
            if(!window.isShowing() || System.nanoTime()-seen<TimeUnit.MILLISECONDS.toNanos(200))continue;
            Parent dialogRoot=window.getScene().getRoot();
            DialogPane pane=dialogRoot instanceof DialogPane direct ? direct : (DialogPane)dialogRoot.lookup(".dialog-pane");
            if(pane==null)continue;
            if(pane.getButtonTypes().contains(ButtonType.YES)) ((Button)pane.lookupButton(confirmChanges?ButtonType.YES:ButtonType.NO)).fire();
            else {
                String content=pane.getContentText();
                if(content!=null && !(content.contains("created.") || content.contains("updated.") || content.contains("deleted.")))errors.add(content);
                var button=pane.lookupButton(ButtonType.OK); if(button instanceof Button b)b.fire();else window.hide();
            }
        }
    }
    @SuppressWarnings("unchecked") private static ComboBox<Object> combo(String id) { return (ComboBox<Object>)root.lookup("#"+id); }
    @SuppressWarnings("unchecked") private static TableView<Object> table() { return (TableView<Object>)root.lookup("#timetableTable"); }
    private static Object field(Object instance,String name) throws Exception { var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance); }
    private static void setField(Object instance,String name,Object value) throws Exception { var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value); }
    private static void invoke(Object instance,String name) throws Exception { var m=instance.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(instance); }
    private static void awaitRows(int count) throws Exception {
        awaitIdle();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            if(fx(() -> isIdle() && table().getItems().size()==count)) {checks++;return;}
            Thread.sleep(30);
        }
        throw new AssertionError("Expected "+count+" timetable rows after refresh; "
                +fx(() -> ((Label)root.lookup("#statusLabel")).getText()+"; disposed="
                +field(field(controller,"tasks"),"disposed")+"; active="+field(field(controller,"tasks"),"active")
                +"; windows="+Window.getWindows().stream().map(w -> w.getClass().getSimpleName()+":"+w.isShowing()).toList()));
    }
    private static void awaitIdle() throws Exception {
        fx(() -> { root.applyCss(); root.layout(); return null; });
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<deadline) {
            if(fx(TimetableViewSmokeTest::isIdle)) {Thread.sleep(100);if(fx(TimetableViewSmokeTest::isIdle))return;}
            Thread.sleep(30);
        }
        throw new AssertionError("Timetable remained busy");
    }
    private static boolean isIdle() {
        return !((Button)root.lookup("#refreshButton")).isDisabled()
                && Window.getWindows().stream().noneMatch(window -> window != stage && window.isShowing());
    }
    private static <T>T fx(Callable<T> action) throws Exception { var task=new FutureTask<>(action);Platform.runLater(task);return task.get(40,TimeUnit.SECONDS); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
}
