package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.dao.AttendanceAccessDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Real MySQL, connection-local copies of live DDL. Never writes production tables.
 * Temporary tables retain enums/checks/indexes; MySQL does not support their foreign keys. */
public final class TimetableDatabaseFixture implements AutoCloseable {
    private static final List<String> TABLES = List.of("departments", "users", "technical_officers", "students",
            "lecturers", "courses", "course_offerings", "lecturer_course_assignments", "enrollments", "timetable_entries");
    private final Connection connection;
    private final Connection borrowed;
    private Runnable afterWrite;
    private final java.util.concurrent.atomic.AtomicReference<SQLException> nextConnectionFailure =
            new java.util.concurrent.atomic.AtomicReference<>();

    public TimetableDatabaseFixture() throws SQLException {
        connection = DBConnection.getConnection();
        try {
            for (String table : TABLES) {
                String ddl;
                try (var statement = connection.prepareStatement("SHOW CREATE TABLE " + table); var rows = statement.executeQuery()) {
                    rows.next();
                    ddl = rows.getString(2).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                            .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R", "")
                            .replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
                }
                try (var statement = connection.prepareStatement(ddl)) { statement.execute(); }
            }
            execute("INSERT INTO departments (department_id,department_code,department_name) VALUES (1,'TT-A','Timetable A'),(2,'TT-B','Timetable B')");
            user(1,"ADMIN",1); user(2,"TECHNICAL_OFFICER",1); user(3,"STUDENT",1);
            user(4,"LECTURER",1); user(5,"LECTURER",2); user(6,"STUDENT",2); user(7,"STUDENT",1);
            execute("INSERT INTO technical_officers (technical_officer_id,staff_no) VALUES (2,'TT-OFFICER')");
            execute("INSERT INTO students (student_id,registration_no,intake_year) VALUES (3,'TT-3',2026),(6,'TT-6',2026),(7,'TT-7',2026)");
            execute("INSERT INTO lecturers (lecturer_id,staff_no) VALUES (4,'TT-L4'),(5,'TT-L5')");
            execute("INSERT INTO courses (course_id,course_code,course_name,credits,department_id) VALUES (1,'TT-A','Timetable A',3,1),(2,'TT-B','Timetable B',3,2)");
            execute("INSERT INTO course_offerings (offering_id,course_id,semester_id,status) VALUES (10,1,1,'ACTIVE'),(11,1,2,'COMPLETED'),(12,1,3,'CANCELLED'),(13,1,4,'PLANNED'),(20,2,1,'ACTIVE')");
            execute("INSERT INTO lecturer_course_assignments (offering_id,lecturer_id,assignment_type) VALUES (10,4,'PRIMARY'),(10,4,'PRACTICAL'),(13,4,'PRIMARY'),(20,5,'PRIMARY')");
            execute("INSERT INTO enrollments (offering_id,student_id,status,attempt_no) VALUES (10,3,'ENROLLED',1),(10,3,'REPEAT',2),(11,3,'REPEAT',1),(12,3,'WITHDRAWN',1),(13,3,'COMPLETED',1),(20,6,'ENROLLED',1)");
            borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        try {
                            Object result = method.invoke(connection, args);
                            if (method.getName().equals("prepareStatement") && afterWrite != null
                                    && args[0].toString().stripLeading().startsWith("INSERT INTO timetable_entries")) {
                                var statement = (java.sql.PreparedStatement) result;
                                return Proxy.newProxyInstance(statement.getClass().getClassLoader(),
                                        new Class<?>[]{java.sql.PreparedStatement.class}, (p, op, values) -> {
                                            try {
                                                Object value = op.invoke(statement, values);
                                                if (op.getName().equals("executeUpdate") && afterWrite != null) {
                                                    Runnable callback = afterWrite; afterWrite = null; callback.run();
                                                }
                                                return value;
                                            } catch (InvocationTargetException error) { throw error.getCause(); }
                                        });
                            }
                            return result;
                        } catch (InvocationTargetException error) { throw error.getCause(); }
                    });
        } catch (SQLException | RuntimeException error) { connection.close(); throw error; }
    }

    private void user(int id, String role, int department) throws SQLException {
        try (var s = connection.prepareStatement("INSERT INTO users (user_id,username,password_hash,role,first_name,last_name,department_id) VALUES (?,?,?,?,'Timetable','Fixture',?)")) {
            s.setInt(1,id); s.setString(2,"tt-fixture-"+id); s.setString(3,java.util.UUID.randomUUID().toString());
            s.setString(4,role); s.setInt(5,department); s.executeUpdate();
        }
    }
    private Connection borrow() throws SQLException {
        SQLException failure = nextConnectionFailure.getAndSet(null);
        if (failure != null) throw failure;
        connection.setReadOnly(false); return borrowed;
    }
    public AdminTimetableService admin() { return new AdminTimetableService(new TimetableDAO(), this::borrow); }
    public TimetableService officer() { return new TimetableService(new TimetableDAO(), new AttendanceAccessDAO(), this::borrow); }
    public StudentTimetableService student() { return new StudentTimetableService(new TimetableDAO(), this::borrow); }
    public void afterNextWrite(Runnable callback) { afterWrite = callback; }
    public void failNextConnection(SQLException failure) { nextConnectionFailure.set(failure); }
    public static void login(int id, String role) { SessionManager.startSession(new User(id,"tt-fixture","Timetable Fixture",null,role,true)); }
    public void execute(String sql) throws SQLException {
        connection.setReadOnly(false);
        try (var s = connection.prepareStatement(sql)) { s.executeUpdate(); }
        if (!connection.getAutoCommit()) connection.commit();
    }
    public int count() throws SQLException {
        try (var s = connection.prepareStatement("SELECT COUNT(*) FROM timetable_entries"); var rows = s.executeQuery()) {
            rows.next(); return rows.getInt(1);
        }
    }
    public static String productionFingerprint() throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var c = DBConnection.getConnection()) {
            c.setReadOnly(true);
            for (String table : TABLES) {
                try (var s = c.prepareStatement("SELECT * FROM " + table + " ORDER BY 1"); var rows = s.executeQuery()) {
                    while (rows.next()) for (int i=1; i<=rows.getMetaData().getColumnCount(); i++) {
                        digest.update(String.valueOf(rows.getObject(i)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte)0);
                    }
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    @Override public void close() throws SQLException { connection.close(); }
}
