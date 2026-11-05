package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Run explicitly with -Dtest=LecturerTimetableMySqlIT. All writes use connection-local temporary tables. */
public class LecturerTimetableMySqlIT extends TestCase {
    private TimetableDatabaseFixture db;
    private String productionBefore;

    @Override protected void setUp() throws Exception {
        productionBefore = TimetableDatabaseFixture.productionFingerprint();
        db = new TimetableDatabaseFixture();
        TimetableDatabaseFixture.login(4, "LECTURER");
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (db != null) db.close();
        } finally {
            SessionManager.logout();
        }
        assertEquals("Persistent data must remain unchanged", productionBefore,
                TimetableDatabaseFixture.productionFingerprint());
    }

    public void testDirectAssignmentsExcludeOtherLecturersAndUnassignedSessions() throws Exception {
        seed();
        var entries = db.lecturer().getMyTimetable();
        assertEquals(List.of(101, 102, 103), entries.stream().map(e -> e.timetableId()).toList());
        assertTrue(entries.stream().allMatch(e -> Integer.valueOf(4).equals(e.lecturerId())));
        // Offering 10 has duplicate course assignments and is shared with lecturer 5.
        // Neither sharing a course nor a department exposes the other lecturer's sessions.
        assertEquals(3, entries.size());
        TimetableDatabaseFixture.login(5, "LECTURER");
        assertEquals(List.of(104), db.lecturer().getMyTimetable().stream().map(e -> e.timetableId()).toList());
    }

    public void testAllDisplayFieldsAndOpenEffectiveDates() throws Exception {
        seed();
        var entries = db.lecturer().getMyTimetable();
        var entry = entries.getFirst();
        assertEquals("MONDAY", entry.dayOfWeek());
        assertEquals(LocalTime.of(8, 30), entry.startTime());
        assertEquals(LocalTime.of(9, 30), entry.endTime());
        assertEquals("TT-A", entry.courseCode());
        assertEquals("Timetable A", entry.courseName());
        assertEquals("GROUP-A", entry.offeringGroup());
        assertEquals("PRACTICAL", entry.sessionType());
        assertEquals("LAB-1", entry.room());
        assertEquals(LocalDate.of(2026, 1, 1), entry.effectiveFrom());
        assertEquals(LocalDate.of(2026, 6, 30), entry.effectiveTo());
        assertNull(entries.get(1).effectiveFrom());
        assertNull(entries.get(1).effectiveTo());
        assertNull(entries.get(1).room());
    }

    public void testEmptyTimetableAndCourseAssignmentsWithoutSessions() throws Exception {
        assertTrue(db.lecturer().getMyTimetable().isEmpty());
        seed();
        db.execute("UPDATE timetable_entries SET lecturer_id=5 WHERE lecturer_id=4");
        assertTrue(db.lecturer().getMyTimetable().isEmpty());
        assertEquals(5, db.count());
    }

    public void testRefreshReflectsReassignmentWithoutChangingRecords() throws Exception {
        seed();
        var service = db.lecturer();
        assertEquals(3, service.getMyTimetable().size());
        db.execute("UPDATE timetable_entries SET lecturer_id=5 WHERE timetable_id=101");
        assertEquals(List.of(102, 103), service.getMyTimetable().stream().map(e -> e.timetableId()).toList());
        assertEquals(5, db.count());
        TimetableDatabaseFixture.login(1, "ADMIN");
        var before = db.admin().getAllTimetable();
        TimetableDatabaseFixture.login(4, "LECTURER");
        db.lecturer().getMyTimetable();
        TimetableDatabaseFixture.login(1, "ADMIN");
        assertEquals(before, db.admin().getAllTimetable());
    }

    public void testDatabaseDeactivationAndRoleChangeAreRejected() throws Exception {
        var service = db.lecturer();
        db.execute("UPDATE users SET is_active=0 WHERE user_id=4");
        LecturerTimetableServiceTest.denied(service::getMyTimetable);
        db.execute("UPDATE users SET is_active=1, role='STUDENT' WHERE user_id=4");
        LecturerTimetableServiceTest.denied(service::getMyTimetable);
    }

    public void testMissingLecturerProfileIsRejected() throws Exception {
        db.execute("DELETE FROM lecturers WHERE lecturer_id=4");
        LecturerTimetableServiceTest.denied(db.lecturer()::getMyTimetable);
    }

    public void testSessionSwitchCannotReuseReader() throws Exception {
        seed();
        var service = db.lecturer();
        service.getMyTimetable();
        TimetableDatabaseFixture.login(5, "LECTURER");
        LecturerTimetableServiceTest.denied(service::getMyTimetable);
        TimetableDatabaseFixture.login(4, "LECTURER");
        LecturerTimetableServiceTest.denied(service::getMyTimetable);
        assertEquals(3, db.lecturer().getMyTimetable().size());
    }

    public void testDatabaseFailureIsNotAnEmptyTimetable() throws Exception {
        var service = db.lecturer();
        db.failNextConnection(new SQLException("Fixture connection unavailable", "08001"));
        try { service.getMyTimetable(); fail("Expected a load failure"); }
        catch (IllegalStateException expected) { assertTrue(expected.getCause() instanceof SQLException); }
        assertTrue(service.getMyTimetable().isEmpty());
    }

    private void seed() throws Exception {
        db.execute("UPDATE course_offerings SET offering_group='GROUP-A' WHERE offering_id=10");
        db.execute("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(10,5,'ASSISTANT')");
        db.execute("""
                INSERT INTO timetable_entries(timetable_id,offering_id,lecturer_id,day_of_week,start_time,end_time,room,session_type,effective_from,effective_to)
                VALUES (103,20,4,'FRIDAY','10:00','11:00','LT-2','LECTURE',NULL,NULL),
                       (102,10,4,'MONDAY','10:00','11:00',NULL,'TUTORIAL',NULL,NULL),
                       (101,10,4,'MONDAY','08:30','09:30','LAB-1','PRACTICAL','2026-01-01','2026-06-30'),
                       (104,10,5,'TUESDAY','08:00','09:00','LT-1','LECTURE',NULL,NULL),
                       (105,10,NULL,'WEDNESDAY','08:00','09:00',NULL,'LECTURE',NULL,NULL)
                """);
    }
}
