package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseResultDAO;
import lk.ac.ruhuna.ftms.dao.EligibilityDAO;
import lk.ac.ruhuna.ftms.dao.GradeRuleDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.exception.ResultCalculationException;
import lk.ac.ruhuna.ftms.model.CourseResult;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.Savepoint;
import java.util.Collections;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.Objects;

/** Real MySQL JDBC checks. All mutations use existing fixtures and are rolled back, never committed. */
public final class ResultPublicationDatabaseTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        CourseResultDAO dao = new CourseResultDAO();
        int enrollmentId;
        CourseResult.PublicationSnapshot original;
        List<List<String>> originalMarks;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                int[] context = readCompleteEnrollment(connection);
                enrollmentId = context[0];
                int offeringId = context[1];
                User actor = readLecturer(connection, offeringId, true);
                User calculator = actor;
                original = dao.getByEnrollmentId(connection, enrollmentId).publicationSnapshot();
                originalMarks = marksSnapshot(connection, enrollmentId);
                // Prepare an unpublished test draft only inside this rollback-only transaction.
                try(var p=connection.prepareStatement("UPDATE course_results SET published=FALSE,published_at=NULL WHERE enrollment_id=?")){
                    p.setInt(1,enrollmentId);p.executeUpdate();
                }
                var attendance=new lk.ac.ruhuna.ftms.dao.AttendanceEligibilityDAO().findByEnrollmentId(connection,enrollmentId);
                check(attendance!=null && !attendance.requiresPolicyDecision(),"fixture attendance is fully marked without special statuses");
                try(var p=connection.prepareStatement("UPDATE eligibility_records SET attendance_percentage=?,attendance_eligible=TRUE,calculated_at=CURRENT_TIMESTAMP WHERE enrollment_id=?")){
                    p.setDouble(1,attendance.getCombinedPercentage());p.setInt(2,enrollmentId);p.executeUpdate();
                }
                int[] commitsRequested = {0};
                Connection rollbackOnly = rollbackOnly(connection, commitsRequested);
                ResultService service = new ResultService(dao, new GradeRuleDAO(), new EligibilityDAO(),
                        () -> rollbackOnly);
                for (String role : new String[]{"ADMIN", "STUDENT", "TECHNICAL_OFFICER"}) {
                    SessionManager.startSession(new User(calculator.getUserId(), "fixture", "Fixture", null, role, true));
                    denied(() -> service.publishResult(offeringId, original), role + " denied");
                }
                SessionManager.logout();
                denied(() -> service.publishResult(offeringId, original), "anonymous denied");
                SessionManager.startSession(readLecturer(connection, offeringId, false));
                check(!service.canPublishResult(offeringId), "real unassigned lecturer cannot publish");
                denied(() -> service.publishResult(offeringId, original), "real unassigned lecturer denied directly");
                denied(() -> service.getBatchResults(offeringId), "unassigned draft read denied");
                SessionManager.startSession(calculator);
                CourseResult draft = service.calculateAndSaveResult(enrollmentId);
                check(!draft.isPublished() && draft.getPublishedAt() == null, "real draft fields");
                check(Objects.equals(calculator.getUserId(), draft.getCalculatedBy()), "real calculator FK");
                check(draft.getResultId() == original.resultId(), "save reuses existing row");
                check(!dao.lockPublicationContext(connection, enrollmentId, Integer.MAX_VALUE), "wrong offering rejected by SQL");
                verifyGpaVisibility(connection, rollbackOnly, context, false);
                SessionManager.startSession(actor);
                check(service.canPublishResult(offeringId), "production policy allows real assigned lecturer");
                Savepoint beforePublication=connection.setSavepoint();
                CourseResult published = service.publishResult(offeringId, draft.publicationSnapshot());
                check(published.isPublished() && published.getPublishedAt() != null, "real publication fields set together");
                check(published.getCalculatedAt().equals(draft.getCalculatedAt()), "publication preserves calculation timestamp");
                check(published.getCalculatedBy().equals(draft.getCalculatedBy()), "publication preserves calculator");
                check(commitsRequested[0] == 2, "draft and publication each request one commit");
                check(dao.getByEnrollmentId(connection, enrollmentId).isPublished(), "JDBC reread sees publication");
                SessionManager.startSession(calculator);
                verifyGpaVisibility(connection, rollbackOnly, context, true);
                SessionManager.startSession(actor);
                try {
                    service.publishResult(offeringId, draft.publicationSnapshot());
                    throw new AssertionError("Duplicate publication accepted");
                } catch (ResultCalculationException expected) {
                    check(expected.getMessage().contains("already published"), "duplicate publication denied");
                }
                check(published.publicationSnapshot().equals(dao.getByEnrollmentId(connection, enrollmentId).publicationSnapshot()),
                        "duplicate request preserves published result and timestamp");
                // An ordinary draft upsert must not act as a second publication route.
                try { dao.saveOrUpdate(connection,published);throw new AssertionError("Published overwrite accepted"); } catch(IllegalStateException expected){checks++;}

                // The transaction has read actual marks, assessments, eligibility and grade rules.
                assertConcurrentWriteBlocked(enrollmentId, "marks", "marks_obtained = marks_obtained", "enrollment_id");
                assertConcurrentWriteBlocked(offeringId, "assessments", "weight_percentage = weight_percentage", "offering_id");
                assertConcurrentWriteBlocked(enrollmentId, "eligibility_records", "overall_eligible = overall_eligible", "enrollment_id");
                assertConcurrentWriteBlocked(draft.getGradeRuleId(), "grade_rules", "grade_point = grade_point", "grade_rule_id");

                connection.rollback(beforePublication);
                CourseResult resaved=dao.getByEnrollmentId(connection,enrollmentId);
                ResultService failing = new ResultService(new CourseResultDAO() {
                    @Override public CourseResult publishDraft(Connection c, int enrollment, int result) throws SQLException {
                        super.publishDraft(c, enrollment, result);
                        throw new SQLException("Injected failure after real UPDATE");
                    }
                }, new GradeRuleDAO(), new EligibilityDAO(), () -> rollbackOnly);
                try {
                    failing.publishResult(offeringId, resaved.publicationSnapshot());
                    throw new AssertionError("Expected transaction failure");
                } catch (IllegalStateException expected) {
                    check(resaved.publicationSnapshot().equals(dao.getByEnrollmentId(connection, enrollmentId).publicationSnapshot()),
                            "real UPDATE rolled back after transaction failure");
                }

                // Change a live input within the test transaction, preserving the reviewed saved draft.
                try (var statement = connection.prepareStatement("""
                        UPDATE marks m JOIN assessments a ON a.assessment_id = m.assessment_id
                        SET m.marks_obtained = CASE WHEN m.marks_obtained = 0 THEN a.max_mark ELSE 0 END
                        WHERE m.enrollment_id = ? AND a.weight_percentage > 0
                        """)) {
                    statement.setInt(1, enrollmentId);
                    check(statement.executeUpdate() > 0, "stale fixture changed marks");
                }
                try {
                    service.publishResult(offeringId, resaved.publicationSnapshot());
                    throw new AssertionError("Changed marks must reject publication");
                } catch (ResultCalculationException expected) {
                    check(expected.getMessage().contains("stale") || expected.getMessage().contains("CA"), "real stale marks rejected");
                }
                check(commitsRequested[0] == 2, "stale result did not request a commit");
            } finally {
                connection.rollback();
                SessionManager.logout();
            }
        }
        check(original.equals(dao.getByEnrollmentId(enrollmentId).publicationSnapshot()), "fresh connection confirms original result fully restored");
        try (Connection verification = DBConnection.getConnection()) {
            check(originalMarks.equals(marksSnapshot(verification, enrollmentId)), "fresh connection confirms every original mark field restored");
        }
        System.out.println("Result publication MySQL checks passed: " + checks
                + "; all writes rolled back (no durable publication performed).");
    }

    private static Connection rollbackOnly(Connection delegate, int[] commits) {
        Savepoint[] operationStart = {null};
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "commit" -> { commits[0]++; return null; }
                        case "close" -> { return null; }
                        case "setAutoCommit" -> {
                            if (!Boolean.FALSE.equals(args[0])) { throw new SQLException("Rollback-only test"); }
                            operationStart[0] = delegate.setSavepoint();
                            return null;
                        }
                        case "rollback" -> { delegate.rollback(operationStart[0]); return null; }
                        // The enclosing test transaction is already SERIALIZABLE, including its draft step.
                        case "setTransactionIsolation" -> { return null; }
                        default -> {
                            try { return method.invoke(delegate, args); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                        }
                    }
                });
    }

    private static List<List<String>> marksSnapshot(Connection connection, int enrollmentId) throws SQLException {
        var snapshot = new ArrayList<List<String>>();
        try (var statement = connection.prepareStatement("SELECT * FROM marks WHERE enrollment_id = ? ORDER BY mark_id")) {
            statement.setInt(1, enrollmentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var values = new ArrayList<String>();
                    for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { values.add(rows.getString(column)); }
                    snapshot.add(values);
                }
            }
        }
        return snapshot;
    }

    private static User readLecturer(Connection connection, int offeringId, boolean assigned) throws SQLException {
        String sql = "SELECT u.user_id, u.role FROM users u WHERE u.role = 'LECTURER' AND u.is_active = TRUE AND "
                + (assigned ? "" : "NOT ") + "EXISTS (SELECT 1 FROM lecturer_course_assignments a WHERE a.lecturer_id=u.user_id AND a.offering_id=?) ORDER BY u.user_id LIMIT 1";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offeringId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) { throw new AssertionError("Existing " + (assigned ? "assigned" : "unassigned") + " lecturer fixture required"); }
                return new User(rows.getInt(1), "fixture", "Fixture", null, rows.getString(2), true);
            }
        }
    }

    /** Runs the unchanged GPAService and DAO against the same rollback-only JDBC transaction. */
    private static void verifyGpaVisibility(Connection real, Connection borrowed, int[] context, boolean published) throws SQLException {
        String url = real.getMetaData().getURL();
        var originals = Collections.list(DriverManager.getDrivers());
        Driver bridge = new Driver() {
            public Connection connect(String requested, Properties info) { return acceptsURL(requested) ? borrowed : null; }
            public boolean acceptsURL(String requested) { return url.equals(requested); }
            public DriverPropertyInfo[] getPropertyInfo(String requested, Properties info) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
        };
        try {
            for (Driver driver : originals) { DriverManager.deregisterDriver(driver); }
            DriverManager.registerDriver(bridge);
            var row = new lk.ac.ruhuna.ftms.dao.SemesterResultDAO().getCourseRowsForSemester(real,context[2],context[3]).stream()
                    .filter(value -> value.enrollmentId() == context[0]).findFirst().orElseThrow();
            check(row.published() == published, "unchanged GPAService sees publication=" + published);
            check(published ? row.gradePoint()!=null : row.gradePoint()==null, "GPA hides draft grade points");
        } finally {
            DriverManager.deregisterDriver(bridge);
            for (Driver driver : originals) { DriverManager.registerDriver(driver); }
        }
    }

    private static User readActor(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT user_id, role FROM users WHERE is_active = TRUE AND role = 'ADMIN' ORDER BY user_id LIMIT 1");
             var rows = statement.executeQuery()) {
            if (!rows.next()) { throw new AssertionError("An existing active ADMIN fixture is required; no account is created."); }
            return new User(rows.getInt(1), "test-fixture", "Test fixture", null, rows.getString(2), true);
        }
    }

    private static int[] readCompleteEnrollment(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT e.enrollment_id, e.offering_id, e.student_id, o.semester_id FROM enrollments e
                JOIN course_offerings o ON o.offering_id = e.offering_id
                JOIN eligibility_records er ON er.enrollment_id = e.enrollment_id AND er.overall_eligible = TRUE
                JOIN course_results r ON r.enrollment_id = e.enrollment_id
                WHERE e.status <> 'WITHDRAWN' AND o.status <> 'CANCELLED'
                  AND (SELECT SUM(a.weight_percentage) FROM assessments a WHERE a.offering_id = e.offering_id) = 100
                  AND NOT EXISTS (SELECT 1 FROM assessments a LEFT JOIN marks m
                      ON m.assessment_id = a.assessment_id AND m.enrollment_id = e.enrollment_id
                      WHERE a.offering_id = e.offering_id AND a.weight_percentage > 0
                        AND (m.mark_status IS NULL OR m.mark_status <> 'PRESENT' OR m.marks_obtained IS NULL))
                ORDER BY e.enrollment_id LIMIT 1
                """); var rows = statement.executeQuery()) {
            if (!rows.next()) { throw new AssertionError("An existing eligible enrollment with complete marks and a result is required."); }
            return new int[]{rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4)};
        }
    }

    private static void assertConcurrentWriteBlocked(int id, String table, String update, String key) throws SQLException {
        // Identifiers here are fixed test literals, never user input.
        try (Connection writer = DBConnection.getConnection()) {
            writer.setAutoCommit(false);
            try (var timeout = writer.createStatement();
                 var statement = writer.prepareStatement("UPDATE " + table + " SET " + update + " WHERE " + key + " = ?")) {
                timeout.execute("SET SESSION innodb_lock_wait_timeout = 1");
                statement.setInt(1, id);
                try {
                    statement.executeUpdate();
                    throw new AssertionError("Concurrent " + table + " writer was not blocked");
                } catch (SQLException expected) {
                    check(expected.getErrorCode() == 1205, "concurrent " + table + " edit waits for publication input locks");
                }
            } finally { writer.rollback(); }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void denied(Action action, String label) throws Exception {
        try { action.run(); } catch (SecurityException expected) { checks++; return; }
        throw new AssertionError(label);
    }
}
