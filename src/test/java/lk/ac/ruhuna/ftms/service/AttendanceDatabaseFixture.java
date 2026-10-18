package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Connection-local MySQL tables shadow production table names. SHOW CREATE TABLE supplies
 * columns/indexes; foreign keys are omitted (unsupported on temporary tables).
 * No production table is altered or written.
 * Closing the connection removes all test data. There is no application test-login mode.
 */
public final class AttendanceDatabaseFixture implements AutoCloseable {
    private final Connection connection;
    private final Connection borrowed;
    private Runnable afterWrite;

    public AttendanceDatabaseFixture() throws SQLException {
        connection = DBConnection.getConnection();
        try {
            for (String table : List.of("departments", "users", "technical_officers", "students", "courses",
                    "course_offerings", "enrollments", "attendance_sessions", "attendance_records")) {
                // Table names come exclusively from this fixed allowlist.
                String ddl;
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SHOW CREATE TABLE " + table)) {
                    rows.next();
                    ddl = rows.getString(2).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                            .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R", "")
                            .replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
                }
                try (var statement = connection.createStatement()) {
                    statement.execute(ddl);
                }
            }
            execute("INSERT INTO departments (department_id,department_code,department_name) VALUES (1,'TEST-A','Test Department A'),(2,'TEST-B','Test Department B')");
            insertUser(1, "TECHNICAL_OFFICER", 1);
            insertUser(2, "STUDENT", 1);
            insertUser(3, "STUDENT", 1);
            insertUser(4, "TECHNICAL_OFFICER", 1); // Deliberately missing officer subtype.
            insertUser(5, "STUDENT", 2);
            execute("INSERT INTO technical_officers (technical_officer_id,staff_no) VALUES (1,'TEST-TO')");
            execute("INSERT INTO students (student_id,registration_no,intake_year) VALUES (2,'TEST-ST-2',2026),(3,'TEST-ST-3',2026),(5,'TEST-ST-5',2026)");
            execute("INSERT INTO courses (course_id,course_code,course_name,credits,department_id) VALUES (1,'TEST-A','Test Course A',3,1),(2,'TEST-B','Test Course B',3,2)");
            execute("INSERT INTO course_offerings (offering_id,course_id,semester_id,status) VALUES (10,1,1,'ACTIVE'),(11,1,2,'ACTIVE'),(20,2,1,'ACTIVE')");
            execute("INSERT INTO enrollments (enrollment_id,offering_id,student_id) VALUES (100,10,2),(101,10,3),(110,11,2),(200,20,5)");
            borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("close")) return null;
                        try {
                            Object result = method.invoke(connection, arguments);
                            if (method.getName().equals("prepareStatement") && afterWrite != null
                                    && arguments[0].toString().stripLeading().startsWith("INSERT INTO attendance_")) {
                                var statement = (java.sql.PreparedStatement) result;
                                return Proxy.newProxyInstance(java.sql.PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{java.sql.PreparedStatement.class}, (p, operation, parameters) -> {
                                            try {
                                                Object value = operation.invoke(statement, parameters);
                                                if (operation.getName().equals("executeUpdate") && afterWrite != null) {
                                                    Runnable callback = afterWrite;
                                                    afterWrite = null;
                                                    callback.run();
                                                }
                                                return value;
                                            } catch (InvocationTargetException exception) { throw exception.getCause(); }
                                        });
                            }
                            return result;
                        }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private void insertUser(int id, String role, int department) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO users (user_id,username,password_hash,role,first_name,last_name,department_id)
                VALUES (?, ?, ?, ?, 'Test', 'Fixture', ?)
                """)) {
            statement.setInt(1, id);
            statement.setString(2, "attendance-fixture-" + id);
            // Deliberately unusable random value, never a hardcoded or usable password.
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, role);
            statement.setInt(5, department);
            statement.executeUpdate();
        }
    }

    public AttendanceService service() { return new AttendanceService(() -> borrowed); }

    public void afterNextAttendanceWrite(Runnable callback) { afterWrite = callback; }

    public static void signInFixture() {
        SessionManager.startSession(new User(1, "attendance-fixture", "Technical Officer",
                "", "TECHNICAL_OFFICER", true));
    }

    public void execute(String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
        if (!connection.getAutoCommit()) connection.commit();
    }

    public int countRecords() throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM attendance_records");
             var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
