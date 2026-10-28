package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import lk.ac.ruhuna.ftms.dao.CourseResultDAO.PublishedStudentResult;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.ResultService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real FXML/Task lifecycle with controlled read delays and failures; no database writes. */
public final class StudentGradesLoadingSmokeTest {
    private static Scene scene;
    private static int checks;
    private record Screen(Parent root, StudentRecordsController controller) {}

    public static void main(String[] args) throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10,TimeUnit.SECONDS),"JavaFX starts");
        try {
            login(3);
            failureAndRecovery();
            cancellation();
            navigationIgnoresLateRead();
            loginChangeIgnoresLateRead();
            login(3);
            timeoutAndRefresh();
            System.out.println("Student grades loading checks passed: " + checks);
        } finally {
            SessionManager.logout();
            Platform.exit();
        }
    }

    private static void failureAndRecovery() throws Exception {
        var calls = new AtomicInteger();
        var offUi = new AtomicBoolean();
        var screen = load(new ResultService() {
            @Override public StudentPublishedRecords getMyPublishedRecords(SessionManager.SessionIdentity identity) {
                offUi.set(!Platform.isFxApplicationThread());
                if (calls.getAndIncrement()==0)
                    throw new IllegalStateException("Wrapped failure",new SQLException("Fixture read unavailable"));
                return records("Recovered");
            }
        });
        ready(screen);
        fx(() -> {
            check(offUi.get(),"database read runs off UI thread");
            check(status(screen).contains("Fixture read unavailable"),"root cause is displayed");
            check(rows(screen).getItems().isEmpty(),"failure has no stale grades");
            check(!node(screen,"summaryBox").isVisible(),"failure hides stale GPA");
            check(!node(screen,"progressIndicator").isVisible(),"failure ends loading");
            refresh(screen).fire();
            return null;
        });
        ready(screen);
        fx(() -> {
            check(rows(screen).getItems().toString().contains("Recovered"),"Refresh recovers after failure");
            check(!node(screen,"progressIndicator").isVisible(),"success ends loading");
            return null;
        });
    }

    private static void cancellation() throws Exception {
        var gate = new Gate();
        try {
            var screen = load(delayed(gate));
            check(gate.entered.await(10,TimeUnit.SECONDS),"cancellable read started");
            fx(() -> {
                var field = StudentRecordsController.class.getDeclaredField("activeTask");
                field.setAccessible(true);
                ((Task<?>)field.get(screen.controller())).cancel(true);
                return null;
            });
            ready(screen);
            fx(() -> {
                check(status(screen).contains("cancelled"),"cancellation explained");
                check(rows(screen).getItems().isEmpty(),"cancellation keeps grades empty");
                check(!node(screen,"progressIndicator").isVisible(),"cancellation ends loading");
                return null;
            });
        } finally { gate.release.countDown(); }
        check(gate.finished.await(10,TimeUnit.SECONDS),"cancelled read exits");
    }

    private static void navigationIgnoresLateRead() throws Exception {
        var gate = new Gate();
        Screen screen;
        var identity = SessionManager.requireSession();
        try {
            screen = load(delayed(gate));
            check(gate.entered.await(10,TimeUnit.SECONDS),"navigation read started");
            fx(() -> { scene.setRoot(new BorderPane(new Label("Destination"))); return null; });
        } finally { gate.release.countDown(); }
        check(gate.finished.await(10,TimeUnit.SECONDS),"detached read exits");
        fx(() -> {
            check(scene.getRoot()!=screen.root(),"destination stays active");
            check(rows(screen).getItems().isEmpty(),"detached screen has no private records");
            check(!node(screen,"progressIndicator").isVisible(),"detached screen ends loading");
            check(SessionManager.isCurrentSession(identity),"navigation preserves login");
            return null;
        });
    }

    private static void loginChangeIgnoresLateRead() throws Exception {
        var gate = new Gate();
        Screen screen;
        try {
            screen = load(delayed(gate));
            check(gate.entered.await(10,TimeUnit.SECONDS),"session-change read started");
            login(4);
        } finally { gate.release.countDown(); }
        ready(screen);
        fx(() -> {
            check(rows(screen).getItems().isEmpty(),"new login receives no prior student's records");
            check(status(screen).contains("session has ended"),"expired screen explains session change");
            check(SessionManager.requireSession().userId()==4,"old callback preserves new login");
            return null;
        });
    }

    private static void timeoutAndRefresh() throws Exception {
        var gate = new Gate();
        var calls = new AtomicInteger();
        Screen screen;
        try {
            screen = load(new ResultService() {
                @Override public StudentPublishedRecords getMyPublishedRecords(SessionManager.SessionIdentity identity) {
                    if (calls.getAndIncrement()==0) { gate.waitForRelease(); return records("Expired"); }
                    return records("Fresh");
                }
            });
            check(gate.entered.await(10,TimeUnit.SECONDS),"timeout read started");
            ready(screen); // Exercise the real 30-second JavaFX timeout.
            fx(() -> {
                check(status(screen).contains("took too long"),"timeout explained");
                check(!node(screen,"progressIndicator").isVisible(),"timeout ends loading");
                refresh(screen).fire();
                return null;
            });
            ready(screen);
        } finally { gate.release.countDown(); }
        check(gate.finished.await(10,TimeUnit.SECONDS),"expired read exits");
        fx(() -> {
            check(rows(screen).getItems().toString().contains("Fresh"),"late result cannot overwrite refreshed grades");
            check(!rows(screen).getItems().toString().contains("Expired"),"expired data discarded");
            return null;
        });
    }

    private static ResultService delayed(Gate gate) {
        return new ResultService() {
            @Override public StudentPublishedRecords getMyPublishedRecords(SessionManager.SessionIdentity identity) {
                gate.waitForRelease();
                return records("Delayed");
            }
        };
    }
    private static final class Gate {
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1), finished=new CountDownLatch(1);
        void waitForRelease() {
            entered.countDown();
            try {
                // Simulate a JDBC driver that cannot interrupt a read immediately.
                while (true) {
                    try { if (!release.await(60,TimeUnit.SECONDS)) throw new AssertionError("Gate not released"); break; }
                    catch (InterruptedException ignored) { }
                }
            } finally { finished.countDown(); }
        }
    }
    private static ResultService.StudentPublishedRecords records(String name) {
        return new ResultService.StudentPublishedRecords(List.of(new PublishedStudentResult(
                "TEST",name,1,"Test semester","A",BigDecimal.valueOf(4),BigDecimal.valueOf(3),1)),List.of());
    }
    private static Screen load(ResultService service) throws Exception {
        return fx(() -> {
            var loader = new FXMLLoader(StudentGradesLoadingSmokeTest.class.getResource("/fxml/student_results.fxml"));
            loader.setControllerFactory(type -> new StudentRecordsController(service));
            Parent root=loader.load();
            if (scene==null) scene=new Scene(root,1100,720); else scene.setRoot(root);
            root.applyCss(); root.layout();
            return new Screen(root,loader.getController());
        });
    }
    private static void ready(Screen screen) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime()<deadline) {
            if (fx(() -> !refresh(screen).isDisabled())) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Loading never completed");
    }
    private static javafx.scene.Node node(Screen screen,String id) { return screen.root().lookup("#"+id); }
    private static Button refresh(Screen screen) { return (Button)node(screen,"refreshButton"); }
    private static TableView<?> rows(Screen screen) { return (TableView<?>)node(screen,"recordsTable"); }
    private static String status(Screen screen) { return ((Label)node(screen,"statusLabel")).getText(); }
    private static void login(int student) { SessionManager.startSession(new User(student,"fixture","Fixture",null,"STUDENT",true)); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task=new FutureTask<>(action); Platform.runLater(task); return task.get(15,TimeUnit.SECONDS);
    }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); checks++; }
}
