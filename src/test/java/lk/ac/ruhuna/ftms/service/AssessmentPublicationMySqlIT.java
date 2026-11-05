package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.AssessmentDAO;
import lk.ac.ruhuna.ftms.model.Assessment;
import lk.ac.ruhuna.ftms.model.Mark;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Explicit MySQL integration tests. All writes use connection-local temporary tables. */
public class AssessmentPublicationMySqlIT extends TestCase {
    private AcademicDatabaseFixture db;
    private AssessmentService lecturer;

    protected void setUp() throws Exception {
        db = new AcademicDatabaseFixture();
        db.login(1, "LECTURER");
        lecturer = new AssessmentService();
    }
    protected void tearDown() throws Exception { db.close(); }

    public void testExistingAndNewAssessmentsAreDraftAndCrudStillWorks() throws Exception {
        assertEquals(0L, ((Number) db.scalar("SELECT COUNT(*) FROM assessments WHERE is_published=TRUE OR published_at IS NOT NULL")).longValue());
        var assessment = new Assessment(20, "Practice", "PRACTICAL", 25, 0, LocalDate.of(2026, 11, 1), "Read chapter 1");
        // Publication fields supplied through CRUD must never release an assessment.
        assessment.setPublished(true);
        assessment.setPublishedAt(java.time.LocalDateTime.now());
        int id = lecturer.save(assessment);
        var saved = lecturer.getAssessments(20).stream().filter(a -> a.getAssessmentId() == id).findFirst().orElseThrow();
        assertFalse(saved.isPublished()); assertNull(saved.getPublishedAt());
        saved.setAssessmentName("Updated practice"); lecturer.save(saved);
        assertEquals("Updated practice", lecturer.getAssessments(20).getLast().getAssessmentName());
        db.login(3, "STUDENT");
        assertTrue(new StudentAssessmentService().getMyAssessments().isEmpty());
        db.login(1, "LECTURER"); lecturer.delete(20, id);
        assertEquals(1, lecturer.getAssessments(20).size());
    }

    public void testReleasePersistsAcrossServiceReloadAndProtectsReleasedRecords() throws Exception {
        lecturer.release(10, 10);
        var released = new AssessmentService().getAssessments(10).getFirst();
        assertTrue(released.isPublished()); assertNotNull(released.getPublishedAt());
        assertEquals("Released", released.getPublicationStatus());
        assertEquals(1L, ((Number) db.scalar("SELECT COUNT(*) FROM assessments WHERE assessment_id=10 AND is_published=TRUE AND published_at IS NOT NULL")).longValue());
        rejected(IllegalStateException.class, () -> lecturer.release(10, 10));
        var timestamp = released.getPublishedAt();
        released.setPublished(false); released.setPublishedAt(null); released.setAssessmentName("Tampered");
        rejected(IllegalStateException.class, () -> lecturer.save(released));
        rejected(IllegalStateException.class, () -> lecturer.delete(10, 10));
        assertEquals(timestamp, new AssessmentService().getAssessments(10).getFirst().getPublishedAt());
        assertEquals("CA", lecturer.getAssessments(10).getFirst().getAssessmentName());
        // DAO predicates also prevent bypass through stale draft objects.
        rejected(java.sql.SQLException.class, () -> new AssessmentDAO().save(db.connection, released, 1));
        rejected(IllegalArgumentException.class, () -> new AssessmentDAO().delete(db.connection, 10, 10));
    }

    public void testStudentsSeeOnlyReleasedEnrolledOfferingsAndRejectAlteredIds() throws Exception {
        lecturer.release(10, 10); lecturer.release(20, 20);
        db.login(2, "LECTURER"); lecturer.release(30, 30);
        db.login(3, "STUDENT");
        var reader = new StudentAssessmentService();
        assertEquals(List.of(10, 20), reader.getMyAssessments().stream().map(r -> r.assessment().getAssessmentId()).toList());
        var assessment = reader.getAssessments(10).getFirst();
        assertEquals("CA", assessment.getAssessmentName()); assertEquals("QUIZ", assessment.getAssessmentType());
        assertEquals(50.0, assessment.getMaxMark(), 0.001); assertEquals(40.0, assessment.getWeightPercentage(), 0.001);
        for (int id : new int[]{30, -1, 999999}) rejected(SecurityException.class, () -> reader.getAssessments(id));
        assertTrue(new AssessmentDAO().findReleasedForStudent(db.connection, 3, 30).isEmpty());
        db.login(4, "STUDENT");
        rejected(SecurityException.class, reader::getMyAssessments);
        var other = new StudentAssessmentService();
        assertEquals(List.of(10, 30), other.getMyAssessments().stream().map(r -> r.assessment().getAssessmentId()).toList());
        rejected(SecurityException.class, () -> other.getAssessments(20));
    }

    public void testEnrollmentAndOfferingStatusesAreRechecked() throws Exception {
        lecturer.release(10, 10);
        db.login(3, "STUDENT"); var reader = new StudentAssessmentService();
        for (String status : List.of("ENROLLED", "REPEAT", "COMPLETED")) {
            db.execute("UPDATE enrollments SET status='" + status + "' WHERE enrollment_id=100");
            assertEquals(1, reader.getAssessments(10).size());
        }
        db.execute("INSERT INTO enrollments(student_id,offering_id,attempt_no,status) VALUES(3,10,2,'REPEAT')");
        assertEquals(1, reader.getMyAssessments().size());
        db.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE student_id=3 AND offering_id=10");
        assertTrue(reader.getMyAssessments().isEmpty());
        rejected(SecurityException.class, () -> reader.getAssessments(10));
        db.execute("UPDATE enrollments SET status='ENROLLED' WHERE enrollment_id=100");
        for (String status : List.of("PLANNED", "ACTIVE", "COMPLETED")) {
            db.execute("UPDATE course_offerings SET status='" + status + "' WHERE offering_id=10");
            assertEquals(1, reader.getAssessments(10).size());
        }
        db.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=10");
        assertTrue(reader.getMyAssessments().isEmpty());
        rejected(SecurityException.class, () -> reader.getAssessments(10));
    }

    public void testLecturerAuthorizationIsEnforcedInService() throws Exception {
        db.login(2, "LECTURER"); rejected(SecurityException.class, () -> lecturer.release(10, 10));
        db.login(1, "LECTURER"); rejected(SecurityException.class, () -> lecturer.release(10, 20));
        rejected(SecurityException.class, () -> lecturer.release(10, 999999));
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=1");
        rejected(SecurityException.class, () -> lecturer.release(10, 10));
        db.execute("UPDATE users SET is_active=TRUE,role='STUDENT' WHERE user_id=1");
        rejected(SecurityException.class, () -> lecturer.release(10, 10));
        db.execute("UPDATE users SET role='LECTURER' WHERE user_id=1");
        db.execute("DELETE FROM lecturers WHERE lecturer_id=1");
        rejected(SecurityException.class, () -> lecturer.release(10, 10));
        assertFalse(lecturerPublished(10));
    }

    public void testAllEstablishedLecturerAssignmentTypesCanRelease() throws Exception {
        db.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=1");
        for (String type : List.of("PRIMARY", "ASSISTANT", "PRACTICAL")) {
            db.execute("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(10,1,'" + type + "')");
            int id = lecturer.save(new Assessment(10, "Assignment " + type, "OTHER", 10, 0, null, null));
            lecturer.release(10, id); assertTrue(lecturerPublished(id));
            db.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=1");
            rejected(SecurityException.class, () -> lecturer.release(10, 11));
        }
    }

    public void testNonLecturersCannotReadManageOrReleasePlans() throws Exception {
        for (var actor : List.of(new Object[]{3, "STUDENT"}, new Object[]{5, "ADMIN"}, new Object[]{6, "TECHNICAL_OFFICER"})) {
            db.login((Integer) actor[0], (String) actor[1]);
            rejected(SecurityException.class, () -> lecturer.getAssessments(10));
            rejected(SecurityException.class, () -> lecturer.save(new Assessment(10, "Forbidden", "QUIZ", 10, 0, null, null)));
            rejected(SecurityException.class, () -> lecturer.save(new Assessment(10, 10, "Forbidden", "QUIZ", 50, 40, null, null)));
            rejected(SecurityException.class, () -> lecturer.delete(10, 10));
            rejected(SecurityException.class, () -> lecturer.release(10, 10));
        }
        SessionManager.logout(); rejected(SecurityException.class, () -> lecturer.release(10, 10));
    }

    public void testStudentAccountProfileRoleAndSessionAreRechecked() throws Exception {
        lecturer.release(10, 10);
        db.login(3, "STUDENT"); var reader = new StudentAssessmentService();
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=3");
        rejected(SecurityException.class, reader::getMyAssessments);
        db.execute("UPDATE users SET is_active=TRUE,role='LECTURER' WHERE user_id=3");
        rejected(SecurityException.class, () -> reader.getAssessments(10));
        db.execute("UPDATE users SET role='STUDENT' WHERE user_id=3");
        db.execute("DELETE FROM students WHERE student_id=3");
        rejected(SecurityException.class, reader::getMyAssessments);
        db.login(3, "STUDENT"); rejected(SecurityException.class, reader::getMyAssessments);
        SessionManager.logout(); rejected(SecurityException.class, reader::getMyAssessments);
    }

    public void testFailedOrInterruptedReleaseRollsBack() throws Exception {
        db.failAfterWrite("UPDATE assessments SET is_published");
        rejected(java.sql.SQLException.class, () -> lecturer.release(10, 10));
        assertFalse(lecturerPublished(10));
        assertNull(db.scalar("SELECT published_at FROM assessments WHERE assessment_id=10"));
        db.afterWrite(SessionManager::logout);
        rejected(SecurityException.class, () -> lecturer.release(10, 10));
        assertFalse(lecturerPublished(10));
        db.login(1, "LECTURER"); db.afterWrite(() -> Thread.currentThread().interrupt());
        try { rejected(java.util.concurrent.CancellationException.class, () -> lecturer.release(10, 10)); }
        finally { Thread.interrupted(); }
        assertFalse(lecturerPublished(10));
    }

    public void testReleaseLeavesMarksResultsEligibilityAndGpaExactlyUnchanged() throws Exception {
        var marks = new MarkService();
        marks.saveMark(new Mark(100, 10, 40.0, 1)); marks.saveMark(new Mark(100, 11, 80.0, 1));
        db.confirmedFixtureEligibility(100);
        var results = new ResultService();
        results.publishResult(10, results.calculateAndSaveResult(100).publicationSnapshot());
        results.calculateAndSaveResult(200);
        var gpa = new GPAService(); gpa.saveSemesterResult(gpa.calculateSemesterGPA(3, 9));
        var before = academicSnapshot();
        lecturer.release(10, 10); lecturer.release(20, 20);
        assertEquals(before, academicSnapshot());
        db.login(3, "STUDENT");
        assertEquals(2, new StudentAssessmentService().getMyAssessments().size());
        assertEquals(1, results.getMyPublishedResults(SessionManager.requireSession()).size());
        assertEquals(before, academicSnapshot());
    }

    public void testAssessmentReleaseDoesNotBlockExistingMarksEntry() throws Exception {
        lecturer.release(10, 10);
        new MarkService().saveMark(new Mark(100, 10, 35.0, 1));
        assertEquals(35.0, ((Number) db.scalar("SELECT marks_obtained FROM marks WHERE assessment_id=10 AND enrollment_id=100")).doubleValue(), 0.001);
        db.login(3, "STUDENT");
        assertEquals(1, new StudentAssessmentService().getMyAssessments().size());
        assertTrue(new ResultService().getMyPublishedResults(SessionManager.requireSession()).isEmpty());
    }

    private boolean lecturerPublished(int id) throws Exception {
        try (var p = db.connection.prepareStatement("SELECT is_published FROM assessments WHERE assessment_id=?")) {
            p.setInt(1, id);
            try (var r = p.executeQuery()) { assertTrue(r.next()); return r.getBoolean(1); }
        }
    }
    private List<List<String>> academicSnapshot() throws Exception {
        var snapshot = new ArrayList<List<String>>();
        for (String table : List.of("marks", "course_results", "semester_results", "eligibility_records")) {
            try (var p = db.connection.prepareStatement("SELECT * FROM " + table + " ORDER BY 1"); var r = p.executeQuery()) {
                while (r.next()) {
                    var row = new ArrayList<String>(); row.add(table);
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) row.add(r.getString(i));
                    snapshot.add(row);
                }
            }
        }
        return snapshot;
    }
    private interface Action { void run() throws Exception; }
    private static void rejected(Class<? extends Exception> type, Action action) throws Exception {
        try { action.run(); fail("Expected " + type.getSimpleName()); }
        catch (Exception error) { if (!type.isInstance(error)) throw error; }
    }
}
