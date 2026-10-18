package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Explicit JDBC runner: only temporary test tables are written; optional live checks are read-only. */
public final class AttendanceIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SessionManager.logout();
        denied(AttendanceService::new);
        try (var fixture = new AttendanceDatabaseFixture()) {
            AttendanceDatabaseFixture.signInFixture();
            var service = fixture.service();
            check(service.getAuthorizedOfferings().stream().map(AttendanceOffering::offeringId).toList().equals(List.of(11, 10)),
                    "only department offerings, with distinct semester choices");
            check(service.getEnrollmentsByOffering(10).size() == 2, "only selected offering enrollments");
            var session = session(10);
            session.setConductedBy(999);
            service.saveSession(session);
            int id = session.getSessionId();
            check(id > 0, "session committed and generated ID returned");
            check(service.getSessionsByOffering(10).getFirst().getConductedBy() == null, "no arbitrary conducted_by");
            var present = new AttendanceRecord(id, 100, "present", 999);
            service.saveAttendance(present);
            service.saveAttendance(new AttendanceRecord(id, 101, "ABSENT", 999));
            var reloaded = fixture.service().getAttendanceBySession(id);
            check(reloaded.size() == 2, "PRESENT and ABSENT saved and reloaded through a fresh service");
            check(reloaded.stream().allMatch(row -> row.getMarkedBy() == 1), "marked_by derived from session");
            check(reloaded.getFirst().getAttendanceStatus().equals("PRESENT"), "status normalized");
            check(service.calculateAttendancePercentage(100) == 100.0, "existing percentage calculation");
            check(service.getSummaryByEnrollment(101).getCombinedPercentage() == 0.0, "absence summary");
            var second = session(10);
            second.setSessionDate(LocalDate.now().minusDays(1));
            service.saveSession(second);
            check(service.getSummariesByOffering(10).getFirst().getUnmarkedCount() == 1, "unmarked session preserved");
            check(service.calculateAttendancePercentage(100) == 50.0, "existing denominator and rounding preserved");
            for (String status : List.of("ABSENT", "LATE", "EXCUSED", "MEDICAL", "PRESENT")) {
                service.saveAttendance(new AttendanceRecord(id, 100, status, 999));
                check(fixture.service().getAttendanceByEnrollment(100).getFirst().getAttendanceStatus().equals(status),
                        "committed update/reload " + status);
                check(fixture.countRecords() == 2, "upsert retains unique session/enrollment pair");
            }
            invalid(() -> service.saveAttendance(new AttendanceRecord(id, 100, "UNKNOWN", 1)));
            var invalidSession = session(10);
            invalidSession.setSessionType("INVALID");
            invalid(() -> service.saveSession(invalidSession));
            invalidSession.setSessionType("THEORY");
            invalidSession.setEndTime(LocalTime.of(8, 0));
            invalid(() -> service.saveSession(invalidSession));
            denied(() -> service.saveAttendance(new AttendanceRecord(id, 110, "PRESENT", 1)));
            denied(() -> service.saveAttendance(new AttendanceRecord(id, 200, "PRESENT", 1)));
            denied(() -> service.saveAttendance(new AttendanceRecord(id, 999, "PRESENT", 1)));
            denied(() -> service.saveAttendance(new AttendanceRecord(999, 100, "PRESENT", 1)));
            denied(() -> service.saveSession(session(20)));
            denied(() -> service.getSessionsByOffering(20));
            denied(() -> service.getEnrollmentsByOffering(20));
            denied(() -> service.getSummariesByOffering(20));
            denied(() -> service.getSummaryByEnrollment(200));
            denied(() -> service.getAttendanceByEnrollment(200));
            denied(() -> service.calculateAttendancePercentage(200));
            fixture.execute("INSERT INTO attendance_sessions (session_id,offering_id,session_date,session_type) VALUES (999,20,CURRENT_DATE,'THEORY')");
            denied(() -> service.getAttendanceBySession(999));
            session.setOfferingId(11);
            denied(() -> service.saveSession(session));
            session.setOfferingId(10);
            denied(() -> service.deleteSession(id));
            check(fixture.countRecords() == 2, "denied writes leave records intact");
            // Nullable legacy times do not break the session selector.
            fixture.execute("INSERT INTO attendance_sessions (offering_id,session_date,session_type) VALUES (10,CURRENT_DATE,'THEORY')");
            check(service.getSessionsByOffering(10).stream().anyMatch(row -> row.getStartTime() == null), "nullable legacy session times load");
            fixture.execute("UPDATE users SET department_id = NULL WHERE user_id = 1");
            denied(service::getAuthorizedOfferings);
            fixture.execute("UPDATE users SET department_id = 2 WHERE user_id = 1");
            denied(() -> service.getAttendanceBySession(id));
            fixture.execute("UPDATE users SET department_id = 1, is_active = 0 WHERE user_id = 1");
            denied(service::getAuthorizedOfferings);
            fixture.execute("UPDATE users SET is_active = 1, role = 'ADMIN' WHERE user_id = 1");
            denied(service::getAuthorizedOfferings);
            fixture.execute("UPDATE users SET role = 'TECHNICAL_OFFICER' WHERE user_id = 1");
            // Invalidate the login after SQL executes but before the service can commit.
            var interrupted = session(11);
            fixture.afterNextAttendanceWrite(SessionManager::logout);
            denied(() -> service.saveSession(interrupted));
            check(interrupted.getSessionId() == 0, "rolled-back insert does not expose a saved session ID");
            AttendanceDatabaseFixture.signInFixture();
            check(fixture.service().getSessionsByOffering(11).isEmpty(), "session switch rolls back the actual insert");
            SessionManager.startSession(new User(4, "fixture", "Fixture", "", "TECHNICAL_OFFICER", true));
            denied(fixture.service()::getAuthorizedOfferings);
            AttendanceDatabaseFixture.signInFixture();
            denied(service::getAuthorizedOfferings); // same account, different login generation
            var current = fixture.service();
            SessionManager.logout();
            denied(current::getAuthorizedOfferings);
            for (String role : List.of("ADMIN", "LECTURER", "STUDENT")) {
                SessionManager.startSession(new User(1, "fixture", "Fixture", "", role, true));
                var forbidden = fixture.service();
                denied(forbidden::getAuthorizedOfferings);
                denied(() -> forbidden.saveSession(session(10)));
                denied(() -> forbidden.saveAttendance(new AttendanceRecord(id, 100, "PRESENT", 1)));
            }
        } finally { SessionManager.logout(); }
        if (List.of(args).contains("--live-read")) liveRead();
        System.out.println("Attendance integration: " + checks + " checks passed. Writes used connection-local MySQL temporary tables only.");
    }

    private static void liveRead() throws Exception {
        int officers = 0;
        try {
            for (var user : new UserDAO().getAllUsers()) {
                if (!user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole())) continue;
                SessionManager.startSession(user);
                var service = new AttendanceService();
                try {
                    var offerings = service.getAuthorizedOfferings();
                    try (var connection = DBConnection.getConnection();
                         var statement = connection.prepareStatement("""
                                 SELECT o.offering_id FROM course_offerings o JOIN courses c ON c.course_id=o.course_id
                                 JOIN users u ON u.department_id=c.department_id WHERE u.user_id=?
                                 """)) {
                        connection.setReadOnly(true);
                        statement.setInt(1, user.getUserId());
                        var expected = new java.util.HashSet<Integer>();
                        try (var rows = statement.executeQuery()) { while (rows.next()) expected.add(rows.getInt(1)); }
                        check(expected.equals(offerings.stream().map(AttendanceOffering::offeringId).collect(java.util.stream.Collectors.toSet())),
                                "live offering scope matches department");
                    }
                    for (var offering : offerings) {
                        service.getEnrollmentsByOffering(offering.offeringId());
                        service.getSummariesByOffering(offering.offeringId());
                        for (var session : service.getSessionsByOffering(offering.offeringId()))
                            service.getAttendanceBySession(session.getSessionId());
                    }
                    officers++;
                } catch (SecurityException missingMapping) {
                    System.out.println("Live account denied: missing/invalid department or officer mapping.");
                }
            }
            System.out.println("Live read-only checks: " + officers + " mapped Technical Officer accounts. Credential login was not exercised.");
        } finally { SessionManager.logout(); }
    }

    private static AttendanceSession session(int offering) {
        return new AttendanceSession(offering, LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(10, 0),
                "THEORY", "Temporary-table integration test", null);
    }
    private interface Work { void run() throws Exception; }
    private static void denied(Work work) throws Exception {
        try { work.run(); throw new AssertionError("Expected access denial"); }
        catch (SecurityException expected) { checks++; }
    }
    private static void invalid(Work work) throws Exception {
        try { work.run(); throw new AssertionError("Expected validation failure"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
