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
            check(service.getAuthorizedOfferings().stream().map(AttendanceOffering::offeringId).toList().equals(List.of(11, 10, 20)),
                    "active offerings across departments without an officer department or profile");
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
            for (int inactive : List.of(12, 13, 14, 30, 999)) {
                denied(() -> service.saveSession(session(inactive)));
                denied(() -> service.getSessionsByOffering(inactive));
                denied(() -> service.getEnrollmentsByOffering(inactive));
                denied(() -> service.getSummariesByOffering(inactive));
            }
            // A cached choice cannot be used after its offering stops being active.
            fixture.execute("UPDATE course_offerings SET status = 'COMPLETED' WHERE offering_id = 10");
            denied(() -> service.saveAttendance(new AttendanceRecord(id, 100, "ABSENT", 1)));
            denied(() -> service.getAttendanceBySession(id));
            fixture.execute("UPDATE course_offerings SET status = 'ACTIVE' WHERE offering_id = 10");
            session.setOfferingId(11);
            denied(() -> service.saveSession(session));
            session.setOfferingId(10);
            denied(() -> service.deleteSession(id));
            check(fixture.countRecords() == 2, "denied writes leave records intact");
            var otherDepartment = session(20);
            service.saveSession(otherDepartment);
            check(service.getSessionsByOffering(20).size() == 1, "other department session created");
            check(service.getEnrollmentsByOffering(20).size() == 1, "other department enrollment loaded");
            service.saveAttendance(new AttendanceRecord(otherDepartment.getSessionId(), 200, "PRESENT", 999));
            check(service.getAttendanceBySession(otherDepartment.getSessionId()).getFirst().getMarkedBy() == 1,
                    "officer without profile or department can save attendance with session-derived audit identity");
            service.saveAttendance(new AttendanceRecord(otherDepartment.getSessionId(), 200, "ABSENT", 999));
            check(service.getAttendanceByEnrollment(200).getFirst().getAttendanceStatus().equals("ABSENT"),
                    "other department attendance update persisted");
            check(service.getSummariesByOffering(20).size() == 1
                    && service.getSummaryByEnrollment(200).getCombinedPercentage() == 0.0
                    && service.calculateAttendancePercentage(200) == 0.0, "other department summaries available");
            // Nullable legacy times do not break the session selector.
            fixture.execute("INSERT INTO attendance_sessions (offering_id,session_date,session_type) VALUES (10,CURRENT_DATE,'THEORY')");
            check(service.getSessionsByOffering(10).stream().anyMatch(row -> row.getStartTime() == null), "nullable legacy session times load");
            fixture.execute("UPDATE users SET department_id = NULL WHERE user_id = 1");
            check(service.getAuthorizedOfferings().size() == 3, "missing department does not restrict attendance");
            fixture.execute("UPDATE users SET department_id = 2 WHERE user_id = 1");
            check(service.getAttendanceBySession(id).size() == 2, "department change does not restrict attendance");
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
            check(fixture.service().getAuthorizedOfferings().size() == 3, "officer profile row is not required");
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
        boolean checkedTec = false;
        try {
            var expected = new java.util.HashSet<Integer>();
            try (var connection = DBConnection.getConnection()) {
                connection.setReadOnly(true);
                try (var statement = connection.prepareStatement("""
                        SELECT o.offering_id FROM course_offerings o JOIN courses c ON c.course_id=o.course_id
                        WHERE o.status='ACTIVE' AND c.is_active=1
                        """); var rows = statement.executeQuery()) {
                    while (rows.next()) expected.add(rows.getInt(1));
                }
            }
            for (var user : new UserDAO().getAllUsers()) {
                if (!user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole())) continue;
                SessionManager.startSession(user);
                var service = new AttendanceService();
                var offerings = service.getAuthorizedOfferings();
                check(expected.equals(offerings.stream().map(AttendanceOffering::offeringId).collect(java.util.stream.Collectors.toSet())),
                        "active offerings available independent of officer department/profile");
                for (var offering : offerings) {
                    service.getEnrollmentsByOffering(offering.offeringId());
                    service.getSummariesByOffering(offering.offeringId());
                    for (var session : service.getSessionsByOffering(offering.offeringId()))
                        service.getAttendanceBySession(session.getSessionId());
                }
                if ("tec".equals(user.getUsername())) {
                    checkedTec = true;
                    check(!offerings.isEmpty(), "tec can load active course offerings");
                    System.out.println("tec: " + offerings.size() + " active offerings loaded; no department/profile prerequisite.");
                }
                officers++;
            }
            check(checkedTec, "live tec account tested");
            System.out.println("Live read-only checks: " + officers + " active Technical Officer accounts. Credential login was not exercised.");
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
