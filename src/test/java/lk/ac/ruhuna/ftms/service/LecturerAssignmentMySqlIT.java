package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.sql.SQLException;
import java.util.List;

/** Explicit MySQL integration tests using connection-local tables copied from the real schema. */
public class LecturerAssignmentMySqlIT extends TestCase {
    private AcademicDatabaseFixture db;
    private LecturerAssignmentService service;

    @Override protected void setUp() throws Exception {
        db = new AcademicDatabaseFixture();
        try {
            // Shadow the remaining history tables too; never mix fixture IDs with live rows.
            for (String table : List.of("course_materials", "timetable_entries", "medicals")) {
                String ddl;
                try (var statement = db.connection.createStatement(); var rows = statement.executeQuery("SHOW CREATE TABLE " + table)) {
                    rows.next(); ddl = rows.getString(2).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                            .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R", "").replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
                }
                db.execute(ddl);
            }
            db.execute("INSERT INTO course_offerings(offering_id,course_id,semester_id,offering_group,status) VALUES(40,1,1,'ADMIN-TEST','ACTIVE')");
            db.login(5,"ADMIN"); service = new LecturerAssignmentService(new LecturerCourseAssignmentDAO());
        } catch (Exception error) {
            db.close(); db = null; throw error;
        }
    }
    @Override protected void tearDown() throws Exception { if (db != null) db.close(); }

    public void testEligibleLecturersUseProfilesAndActiveRole() throws Exception {
        assertEquals(2, service.getEligibleLecturers().size());
        db.execute("UPDATE users SET is_active=0 WHERE user_id=2");
        assertEquals(1, service.getEligibleLecturers().size());
        expect(IllegalArgumentException.class, () -> service.assignLecturer(40,2,AssignmentType.PRIMARY));
        db.execute("UPDATE users SET is_active=1,role='ADMIN' WHERE user_id=2");
        assertEquals(1, service.getEligibleLecturers().size());
        expect(IllegalArgumentException.class, () -> service.assignLecturer(40,2,AssignmentType.PRIMARY));
        db.execute("INSERT INTO users(user_id,username,password_hash,role,first_name,last_name,is_active) VALUES(7,'no-profile','test-only','LECTURER','No','Profile',1)");
        assertEquals(1, service.getEligibleLecturers().size());
        expect(IllegalArgumentException.class, () -> service.assignLecturer(40,7,AssignmentType.PRIMARY));
    }

    public void testAssignMultipleDuplicateUpdateAndRemoval() throws Exception {
        int first = service.assignLecturer(40,1,AssignmentType.PRIMARY);
        int second = service.assignLecturer(40,2,AssignmentType.PRACTICAL);
        assertEquals(2, service.getAssignmentsByOffering(40).size());
        for (AssignmentType type : AssignmentType.values()) {
            expectSql(1062, () -> service.assignLecturer(40,1,type));
            service.updateAssignmentType(40,first,type);
            assertEquals(type, service.getAssignmentsByOffering(40).stream().filter(a -> a.getAssignmentId()==first).findFirst().orElseThrow().getAssignmentType());
        }
        assertNull(service.getAssignmentChangeBlocker(40,first));
        service.removeAssignment(40,first);
        assertEquals(1, service.getAssignmentsByOffering(40).size());
        service.removeAssignment(40,second); assertTrue(service.getAssignmentsByOffering(40).isEmpty());
        assertEquals(1, ((Number)db.scalar("SELECT COUNT(*) FROM course_offerings WHERE offering_id=40")).intValue());
        assertEquals(2, ((Number)db.scalar("SELECT COUNT(*) FROM lecturers")).intValue());
    }

    public void testEachHistoryCategoryBlocksRemovalAndTypeChange() throws Exception {
        String[][] history = {
            {"assessments", "INSERT INTO assessments(assessment_id,offering_id,assessment_name,max_mark,weight_percentage) VALUES(90,40,'History',100,100)", "DELETE FROM assessments WHERE assessment_id=90"},
            {"marks", "INSERT INTO marks(assessment_id,enrollment_id,marks_obtained,entered_by) VALUES(30,400,50,1)", "DELETE FROM marks WHERE enrollment_id=400"},
            {"results", "INSERT INTO course_results(enrollment_id,published,calculated_by) VALUES(400,1,1)", "DELETE FROM course_results WHERE enrollment_id=400"},
            {"eligibility", "INSERT INTO eligibility_records(enrollment_id,calculated_by) VALUES(400,1)", "DELETE FROM eligibility_records WHERE enrollment_id=400"},
            {"materials", "INSERT INTO course_materials(offering_id,title,uploaded_by) VALUES(40,'History',1)", "DELETE FROM course_materials WHERE offering_id=40"},
            {"attendance", "INSERT INTO attendance_sessions(offering_id,session_date,conducted_by) VALUES(40,'2026-01-01',1)", "DELETE FROM attendance_sessions WHERE offering_id=40"},
            {"timetable", "INSERT INTO timetable_entries(offering_id,lecturer_id,day_of_week,start_time,end_time) VALUES(40,1,'MONDAY','08:00','09:00')", "DELETE FROM timetable_entries WHERE offering_id=40"},
            {"medical", "INSERT INTO medicals(student_id,offering_id,from_date,to_date,reviewed_by) VALUES(3,40,'2026-01-01','2026-01-01',1)", "DELETE FROM medicals WHERE offering_id=40"}
        };
        int assignment = service.assignLecturer(40,1,AssignmentType.PRIMARY);
        db.execute("INSERT INTO enrollments(enrollment_id,offering_id,student_id) VALUES(400,40,3)");
        for (String[] category : history) {
            db.execute(category[1]);
            assertNotNull(category[0], service.getAssignmentChangeBlocker(40,assignment));
            expect(IllegalStateException.class, () -> service.removeAssignment(40,assignment));
            expect(IllegalStateException.class, () -> service.updateAssignmentType(40,assignment,AssignmentType.ASSISTANT));
            assertEquals(AssignmentType.PRIMARY, service.getAssignmentsByOffering(40).getFirst().getAssignmentType());
            if (category[0].equals("results")) assertEquals(1, ((Number)db.scalar("SELECT COUNT(*) FROM course_results WHERE enrollment_id=400 AND published=1")).intValue());
            db.execute(category[2]); // Only this connection's temporary fixture rows.
        }
        assertNull(service.getAssignmentChangeBlocker(40,assignment));
    }

    public void testCancelledMissingAndStaleSelectionsRejected() throws Exception {
        int assignment = service.assignLecturer(40,1,AssignmentType.PRIMARY);
        expect(IllegalArgumentException.class, () -> service.removeAssignment(20,assignment));
        expect(IllegalArgumentException.class, () -> service.assignLecturer(9999,1,AssignmentType.PRIMARY));
        db.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=40");
        assertEquals(1, service.getAssignmentsByOffering(40).size());
        expect(IllegalStateException.class, () -> service.assignLecturer(40,2,AssignmentType.PRIMARY));
        expect(IllegalStateException.class, () -> service.updateAssignmentType(40,assignment,AssignmentType.PRACTICAL));
        expect(IllegalStateException.class, () -> service.removeAssignment(40,assignment));
    }

    public void testLecturerReadsAndAcademicAuthorizationRemainScoped() throws Exception {
        int assignment = service.assignLecturer(40,1,AssignmentType.PRACTICAL);
        db.login(1,"LECTURER");
        assertTrue(new LecturerViewService().offerings().stream().anyMatch(o -> o.offeringId()==40));
        assertTrue(new ResultService().getOfferings().stream().anyMatch(o -> o.offeringId()==40));
        new LecturerAccessService().requireOffering(40);
        assertTrue(new ResultService().getBatchResults(40).isEmpty());
        expect(SecurityException.class, () -> new LecturerAccessService().requireOffering(30));
        expect(SecurityException.class, () -> new MarkService().saveMark(new Mark(0,300,30,50.0,1)));
        expect(SecurityException.class, () -> new ResultService().getBatchResults(30));
        db.login(5,"ADMIN"); service = new LecturerAssignmentService(new LecturerCourseAssignmentDAO());
        service.removeAssignment(40,assignment);
        db.login(1,"LECTURER");
        assertFalse(new LecturerViewService().offerings().stream().anyMatch(o -> o.offeringId()==40));
        expect(SecurityException.class, () -> new LecturerAccessService().requireOffering(40));
    }

    public void testDatabaseAccountRecheckAndTransactionRollback() throws Exception {
        db.execute("UPDATE users SET is_active=0 WHERE user_id=5");
        expect(SecurityException.class, service::getEligibleLecturers);
        db.execute("UPDATE users SET is_active=1 WHERE user_id=5");
        db.failAfterWrite("INSERT INTO lecturer_course_assignments");
        expect(SQLException.class, () -> service.assignLecturer(40,1,AssignmentType.PRIMARY));
        assertTrue(service.getAssignmentsByOffering(40).isEmpty());
        int assignment = service.assignLecturer(40,1,AssignmentType.PRIMARY);
        db.failAfterWrite("DELETE FROM lecturer_course_assignments");
        expect(SQLException.class, () -> service.removeAssignment(40,assignment));
        assertEquals(1, service.getAssignmentsByOffering(40).size());
        db.afterWrite(SessionManager::logout);
        expect(SecurityException.class, () -> service.updateAssignmentType(40,assignment,AssignmentType.ASSISTANT));
        db.login(5,"ADMIN"); service = new LecturerAssignmentService(new LecturerCourseAssignmentDAO());
        assertEquals(AssignmentType.PRIMARY, service.getAssignmentsByOffering(40).getFirst().getAssignmentType());
    }

    private interface Work { void run() throws Exception; }
    private static void expect(Class<? extends Exception> type, Work work) throws Exception {
        try { work.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw e; }
        fail("Expected " + type.getSimpleName());
    }
    private static void expectSql(int code, Work work) throws Exception {
        try { work.run(); } catch (SQLException e) { assertEquals(code,e.getErrorCode()); return; }
        fail("Expected SQL error " + code);
    }
}
