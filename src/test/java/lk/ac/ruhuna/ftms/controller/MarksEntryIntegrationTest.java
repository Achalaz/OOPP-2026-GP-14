package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.MarkService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Explicit live-MySQL/real-FXML runner. Every write is contained in a rollback-only connection. */
public final class MarksEntryIntegrationTest {
    private static int checks;
    private static Scene scene;

    public static void main(String[] args) throws Exception {
        if (!List.of(args).contains("--rollback-write"))
            throw new IllegalArgumentException("Run explicitly with --rollback-write on the local test database.");

        try (Connection database = DBConnection.getConnection()) {
            Map<String, List<List<String>>> before = snapshot(database);
            try (RollbackConnections fixture = new RollbackConnections(database)) {
                serviceChecks(database);
                uiChecks();
                check(fixture.commits >= 3, "service and UI saves reached the transaction commit boundary");
            } finally {
                SessionManager.logout();
            }
            check(before.equals(snapshot(database)), "all rows in every touched table preserved after rollback");
        }
        System.out.println("Original Marks integration: " + checks
                + " checks passed; real MySQL SQL and FXML, all writes rolled back.");
    }

    private static User lecturer() throws SQLException {
        for (User user : new UserDAO().getAllUsers()) {
            if (user.isActive() && "LECTURER".equals(user.getRole())
                    && !new LecturerCourseAssignmentDAO().findAssigned(user.getUserId()).isEmpty()) return user;
        }
        throw new AssertionError("Test database needs an active lecturer with assigned marks.");
    }

    private static void serviceChecks(Connection database) throws Exception {
        SessionManager.logout();
        denied(() -> new MarkService().getAllMarks());
        denied(() -> new MarkService().saveMark(new Mark(1, 1, 10.0, 1)));
        for (String role : List.of("STUDENT", "TECHNICAL_OFFICER", "ADMIN")) {
            SessionManager.startSession(new User(1, "fixture", "Fixture", "fixture@example.org", role, true));
            denied(() -> new MarkService().saveMark(new Mark(1, 1, 10.0, 1)));
            if (!role.equals("ADMIN")) denied(() -> new MarkService().getAllMarks());
        }
        try {
            SessionManager.startSession(new User(1, "fixture", "Fixture", "fixture@example.org", "LECTURER", false));
            throw new AssertionError("Inactive session allowed");
        } catch (IllegalArgumentException expected) { checks++; }

        User lecturer = lecturer();
        SessionManager.startSession(lecturer);
        MarkService service = new MarkService();
        List<Mark> rows = service.getAllMarks();
        check(!rows.isEmpty(), "assigned marks loaded");
        Mark own = rows.getFirst();
        var assignments = new LecturerCourseAssignmentDAO();
        int offering = assignments.enrollmentOffering(own.getEnrollmentId());
        for (Mark row : rows) {
            int course = assignments.enrollmentOffering(row.getEnrollmentId(), row.getAssessmentId(), row.getAttemptNo());
            check(assignments.isAssigned(lecturer.getUserId(), course), "loaded mark belongs to lecturer");
        }
        Mark other = null;
        for (Mark row : new MarkDAO().findAll()) {
            if (!assignments.isAssigned(lecturer.getUserId(), assignments.enrollmentOffering(row.getEnrollmentId()))) {
                other = row; break;
            }
        }
        check(other != null, "test database contains an unassigned course");
        Mark unauthorized = other;
        check(rows.stream().noneMatch(row -> row.getMarkId() == unauthorized.getMarkId()), "unassigned mark hidden");
        denied(() -> service.saveMark(unauthorized));
        denied(() -> service.saveMark(new Mark(own.getEnrollmentId(), unauthorized.getAssessmentId(), 10.0, own.getAttemptNo())));
        denied(() -> service.saveMark(new Mark(own.getEnrollmentId(), own.getAssessmentId(), 10.0, own.getAttemptNo() + 1)));
        denied(() -> service.deleteMark(own.getMarkId()));
        double max = new AssessmentDAO().getMaxMarkById(own.getAssessmentId());
        for (double invalid : new double[]{-1, max + 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { service.saveMark(copy(own, invalid)); throw new AssertionError("Invalid mark allowed"); }
            catch (InvalidMarkException expected) { checks++; }
        }

        Savepoint active = database.setSavepoint();
        update(database, "UPDATE users SET is_active=FALSE WHERE user_id=?", lecturer.getUserId());
        denied(service::getAllMarks);
        denied(() -> service.saveMark(copy(own, 10.0)));
        database.rollback(active);

        Savepoint assigned = database.setSavepoint();
        update(database, "DELETE FROM lecturer_course_assignments WHERE lecturer_id=? AND offering_id=?", lecturer.getUserId(), offering);
        denied(() -> service.saveMark(copy(own, 10.0)));
        check(service.getAllMarks().stream().noneMatch(row -> row.getMarkId() == own.getMarkId()), "revoked assignment hidden");
        database.rollback(assigned);

        Savepoint allAssignments = database.setSavepoint();
        update(database, "DELETE FROM lecturer_course_assignments WHERE lecturer_id=?", lecturer.getUserId());
        check(service.getAllMarks().isEmpty(), "no assignments gives an empty marks list");
        denied(() -> service.saveMark(copy(own, 10.0)));
        database.rollback(allAssignments);

        Savepoint enrollment = database.setSavepoint();
        update(database, "UPDATE enrollments SET status='WITHDRAWN' WHERE enrollment_id=?", own.getEnrollmentId());
        denied(() -> service.saveMark(copy(own, 10.0)));
        check(service.getAllMarks().stream().noneMatch(row -> row.getMarkId() == own.getMarkId()), "withdrawn enrollment hidden");
        database.rollback(enrollment);

        for (Double value : Arrays.asList(0.0, max, null)) {
            service.saveMark(copy(own, value));
            Mark saved = service.getAllMarks().stream().filter(row -> row.getMarkId() == own.getMarkId()).findFirst().orElseThrow();
            check(Objects.equals(value, saved.getMarksObtained()), "zero, maximum and pending values survive database mapping");
        }
        try (var ps = database.prepareStatement("SELECT entered_by,mark_status FROM marks WHERE mark_id=?")) {
            ps.setInt(1, own.getMarkId());
            try (var rs = ps.executeQuery()) {
                check(rs.next() && rs.getInt(1) == lecturer.getUserId() && rs.getString(2).equals("PENDING"), "save audit/status preserved");
            }
        }
        try (var ps = database.prepareStatement("SELECT COUNT(*) FROM course_results WHERE enrollment_id=? AND published=TRUE")) {
            ps.setInt(1, own.getEnrollmentId());
            try (var rs = ps.executeQuery()) { rs.next(); check(rs.getInt(1) == 0, "changed marks invalidate published results"); }
        }
        SessionManager.startSession(lecturer);
        denied(service::getAllMarks);
        denied(() -> service.saveMark(copy(own, 10.0)));
        SessionManager.logout();
        denied(service::getAllMarks);
    }

    private static void uiChecks() throws Exception {
        User lecturer = lecturer();
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        check(started.await(10, TimeUnit.SECONDS), "JavaFX startup");
        try {
            fx(() -> {
                Platform.setImplicitExit(false);
                SessionManager.startSession(lecturer);
                var identity = SessionManager.requireSession();
                var dashboard = new FXMLLoader(MarksEntryIntegrationTest.class.getResource("/fxml/lecturer_dashboard.fxml"));
                scene = new Scene(dashboard.load(), 1250, 800);
                check(dashboard.getController() instanceof LecturerDashboardController, "dashboard controller");
                for (String entry : List.of("Upload Marks", "Upload Exam Marks")) {
                    button(entry).fire();
                    check(scene.getRoot().lookup("#marksTable") instanceof TableView<?>, "dashboard opens original marks FXML: " + entry);
                    check(scene.getRoot().lookup("#saveButton") != null, "original save handler wired");
                    check(!scene.getRoot().lookup("#deleteButton").isVisible(), "lecturer delete hidden");
                    button("\u2190 Back to Dashboard").fire();
                    check(scene.getRoot().getId().equals("lecturerRoot"), "marks returns to lecturer dashboard");
                    check(SessionManager.isCurrentSession(identity), "dashboard return preserves exact session");
                }
                var marksLoader = new FXMLLoader(MarksEntryIntegrationTest.class.getResource("/fxml/marks_entry.fxml"));
                scene.setRoot(marksLoader.load());
                check(marksLoader.getController() instanceof MarksController, "original MarksController loaded");
                @SuppressWarnings("unchecked") TableView<Mark> table = (TableView<Mark>) scene.getRoot().lookup("#marksTable");
                table.getSelectionModel().selectFirst();
                Mark selected = table.getSelectionModel().getSelectedItem();
                check(field("enrollmentIdField").getText().equals(String.valueOf(selected.getEnrollmentId())), "selection fills original form");
                field("markField").setText("0");
                fireAndDismiss("Save / Update", "Mark saved successfully.");
                check(field("enrollmentIdField").getText().isEmpty(), "save clears the form");
                check(table.getItems().stream().anyMatch(row -> row.getMarkId() == selected.getMarkId()
                        && Objects.equals(row.getMarksObtained(), 0.0)), "saved mark reloaded in original table");
                table.getSelectionModel().selectFirst();
                button("Clear").fire();
                check(field("markField").getText().isEmpty() && field("attemptNoField").getText().equals("1"), "original clear handler");
                return null;
            });
            for (String entry : List.of("Eligibility", "Results & Grades")) {
                fx(() -> { button(entry).fire(); return null; });
                awaitWorkspace();
                fx(() -> {
                    check(scene.getRoot().getId().equals("workspaceRoot"), "authorized academic integration: " + entry);
                    check(!scene.getRoot().lookup("#markEditor").isVisible(), "academic view is read-only");
                    check(!((ComboBox<?>) scene.getRoot().lookup("#offering")).getItems().isEmpty(), "academic view loads assigned offerings");
                    button("\u2190 Back to Dashboard").fire(); button("Upload Marks").fire(); return null;
                });
            }
            fx(() -> {
                SessionManager.logout();
                try { FXMLLoader.load(MarksEntryIntegrationTest.class.getResource("/fxml/marks_entry.fxml"));
                    throw new AssertionError("Unauthenticated marks screen allowed");
                } catch (java.io.IOException expected) { checks++; }
                return null;
            });
        } finally { Platform.exit(); }
    }

    private static void awaitWorkspace() throws Exception {
        for (int i = 0; i < 200; i++) {
            if (fx(() -> !scene.getRoot().lookup("#refresh").isDisabled())) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Academic workspace load timed out");
    }

    private static void fireAndDismiss(String text, String expectedMessage) throws Exception {
        CompletableFuture<String> message = new CompletableFuture<>();
        Platform.runLater(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window.getScene().getRoot() instanceof DialogPane dialog) {
                    message.complete(dialog.getContentText()); window.hide(); return;
                }
            }
            message.completeExceptionally(new AssertionError("Save alert missing"));
        });
        button(text).fire();
        check(message.get(5, TimeUnit.SECONDS).equals(expectedMessage), "original save alert and handler");
    }

    private static Button button(String text) {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        return scene.getRoot().lookupAll(".button").stream().filter(node -> node instanceof Button b && b.getText().strip().equals(text))
                .map(node -> (Button) node).findFirst().orElseThrow();
    }
    private static TextField field(String id) { return (TextField) scene.getRoot().lookup("#" + id); }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    private static Mark copy(Mark mark, Double value) {
        return new Mark(mark.getEnrollmentId(), mark.getAssessmentId(), value, mark.getAttemptNo());
    }
    private static void update(Connection database, String sql, int... values) throws SQLException {
        try (var ps = database.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setInt(i + 1, values[i]);
            ps.executeUpdate();
        }
    }
    private static void denied(Runnable action) {
        try { action.run(); throw new AssertionError("Expected access denial"); }
        catch (SecurityException expected) { checks++; }
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description); checks++;
    }
    private static Map<String, List<List<String>>> snapshot(Connection database) throws SQLException {
        Map<String, List<List<String>>> snapshot = new TreeMap<>();
        for (String table : List.of("marks", "course_results", "users", "enrollments", "lecturer_course_assignments")) {
            List<List<String>> rows = new ArrayList<>();
            try (var ps = database.prepareStatement("SELECT * FROM " + table + " ORDER BY 1,2"); var rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<String> values = new ArrayList<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) values.add(rs.getString(i));
                    rows.add(values);
                }
            }
            snapshot.put(table, rows);
        }
        return snapshot;
    }

    /** Uses real JDBC SQL; only commit/close are intercepted so application code cannot persist test writes. */
    private static final class RollbackConnections implements Driver, AutoCloseable {
        private final Connection database;
        private final Connection borrowed;
        private final List<Driver> originalDrivers;
        private final String url;
        private int commits;

        RollbackConnections(Connection database) throws SQLException {
            this.database = database;
            url = database.getMetaData().getURL();
            try (var ps = database.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema=DATABASE() AND engine='InnoDB'
                      AND table_name IN ('marks','course_results','users','enrollments','lecturer_course_assignments')
                    """); var rs = ps.executeQuery()) {
                rs.next();
                check(rs.getInt(1) == 5, "all test writes use transactional InnoDB tables");
            }
            database.setAutoCommit(false);
            borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        if (method.getName().equals("commit")) { commits++; return null; }
                        if (method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(args[0]))
                            throw new SQLException("Test connection must remain rollback-only");
                        try { return method.invoke(database, args); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    });
            originalDrivers = Collections.list(DriverManager.getDrivers());
            for (Driver driver : originalDrivers) DriverManager.deregisterDriver(driver);
            DriverManager.registerDriver(this);
        }
        @Override public Connection connect(String requested, Properties properties) { return acceptsURL(requested) ? borrowed : null; }
        @Override public boolean acceptsURL(String requested) { return url.equals(requested); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public void close() throws SQLException {
            try { database.rollback(); database.setAutoCommit(true); }
            finally {
                DriverManager.deregisterDriver(this);
                for (Driver driver : originalDrivers) DriverManager.registerDriver(driver);
            }
        }
    }
}
