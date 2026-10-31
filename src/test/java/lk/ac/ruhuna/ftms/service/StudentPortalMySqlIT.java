package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.Set;
import java.util.stream.Collectors;

public class StudentPortalMySqlIT extends TestCase {
    private StudentDatabaseFixture db;
    protected void setUp() throws Exception { db = new StudentDatabaseFixture(); db.academic.login(3,"STUDENT"); }
    protected void tearDown() throws Exception { db.close(); }

    public void testAuthenticationProfileContactsAndOwnership() throws Exception {
        SessionManager.logout();
        var auth = new AuthService();
        assertTrue(auth.authenticate("academic-test-3","incorrect".toCharArray()).isEmpty());
        var user = auth.authenticate("academic-test-3",db.password.toCharArray()).orElseThrow();
        assertEquals("STUDENT",user.getRole());
        SessionManager.startSession(user);
        var profile = new ProfileService();
        String name = profile.getMyProfile().getFullName();
        Object hash = db.academic.scalar("SELECT password_hash FROM users WHERE user_id=3");
        var changed = profile.updateMyStudentProfile(name,"new@example.edu","+94 12345678");
        assertEquals("+94 12345678",changed.getPhone());
        assertEquals("new@example.edu",profile.getMyProfile().getEmail());
        assertEquals("+94 12345678",profile.getMyProfile().getPhone());
        assertEquals(name,profile.getMyProfile().getFullName());
        StudentAccessTest.denied(() -> profile.updateMyProfile("Forged Name","new@example.edu"));
        StudentAccessTest.denied(() -> new lk.ac.ruhuna.ftms.dao.ProfileDAO().getProfile(4));
        assertEquals(hash,db.academic.scalar("SELECT password_hash FROM users WHERE user_id=3"));
        assertEquals("academic-test-3",db.academic.scalar("SELECT username FROM users WHERE user_id=3"));
        assertEquals("STUDENT",db.academic.scalar("SELECT role FROM users WHERE user_id=3"));
        assertEquals("TEST/3",db.academic.scalar("SELECT registration_no FROM students WHERE student_id=3"));
        SessionManager.logout();
        StudentAccessTest.denied(profile::getMyProfile);
    }

    public void testAttendanceAndCoursesAreSelfOnlyAndReadOnly() throws Exception {
        db.academic.execute("UPDATE attendance_records SET attendance_status='ABSENT' WHERE enrollment_id=100 AND session_id=2");
        var attendance = new AttendanceService();
        var view = attendance.getMyAttendance();
        assertEquals(2,view.summaries().size());
        assertEquals(3,view.sessions().size());
        var first = view.summaries().getFirst().attendance();
        assertEquals(100.0,first.getTheoryPercentage(),0.001);
        assertEquals(0.0,first.getPracticalPercentage(),0.001);
        assertEquals(50.0,first.getCombinedPercentage(),0.001);
        assertEquals(Set.of("PRESENT","ABSENT"),view.sessions().stream().map(AttendanceService.StudentSession::status).collect(Collectors.toSet()));
        assertEquals(Set.of(100,200),new StudentCourseService().getMyCourses().stream()
                .map(lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse::enrollmentId).collect(Collectors.toSet()));
        StudentAccessTest.denied(() -> attendance.getAttendanceByEnrollment(101));
        StudentAccessTest.denied(() -> attendance.saveAttendance(null));
        db.academic.execute("DELETE FROM attendance_records WHERE enrollment_id=100 AND session_id=1");
        assertTrue(attendance.getMyAttendance().sessions().stream().anyMatch(s -> s.status()==null));
        db.academic.execute("UPDATE users SET is_active=FALSE WHERE user_id=3");
        StudentAccessTest.denied(attendance::getMyAttendance);
    }

    public void testMedicalStatusesAndNotices() throws Exception {
        var medical = new MedicalService();
        var records = medical.getMyMedicals(SessionManager.requireSession());
        assertEquals(3,records.size());
        assertTrue(records.stream().allMatch(m -> m.getStudentId()==3));
        assertEquals(Set.of("PENDING","APPROVED","REJECTED"),records.stream().map(m -> m.getStatus()).collect(Collectors.toSet()));
        StudentAccessTest.denied(() -> medical.approveMedical(1,3,"forged"));
        StudentAccessTest.denied(() -> medical.getMedicalsByStudent(4));
        assertEquals("PENDING",db.academic.scalar("SELECT status FROM medicals WHERE medical_id=1"));
        var notices = new NoticeService(new lk.ac.ruhuna.ftms.dao.NoticeDAO());
        assertEquals(Set.of("All","Students"),notices.getPublishedNotices().stream().map(n -> n.getTitle()).collect(Collectors.toSet()));
        StudentAccessTest.denied(() -> notices.setPublished(1,false));
    }

    public void testPublishedResultsGpaAndDraftPrivacy() throws Exception {
        var results = new ResultService();
        assertTrue(results.getMyPublishedResults(SessionManager.requireSession()).isEmpty());
        var empty = results.getMyPublishedRecords(SessionManager.requireSession());
        assertTrue(empty.grades().isEmpty());
        assertTrue(empty.semesters().isEmpty());
        assertEquals(ResultService.NO_PUBLISHED_RESULTS,empty.emptyMessage());
        db.publishSamples();
        db.academic.login(3,"STUDENT");
        var identity = SessionManager.requireSession();
        var rows = results.getMyPublishedResults(identity);
        assertEquals(1,rows.size());
        assertEquals("TEST-A",rows.getFirst().courseCode());
        assertEquals("A",rows.getFirst().grade());
        var gpa = new GPAService();
        var semesters = gpa.getMyPublishedGpa(identity);
        assertEquals(1,semesters.size());
        assertEquals(4.0,semesters.getFirst().result().getSgpa(),0.001);
        assertEquals(4.0,semesters.getFirst().result().getCgpa(),0.001);
        assertTrue(semesters.getFirst().status().contains("No saved GPA summary"));
        assertEquals(0L,((Number)db.academic.scalar("SELECT COUNT(*) FROM semester_results")).longValue());
        saveGpa();
        identity = SessionManager.requireSession();
        semesters = gpa.getMyPublishedGpa(identity);
        assertEquals(4.0,semesters.getFirst().result().getSgpa(),0.001);
        assertEquals(4.0,semesters.getFirst().result().getCgpa(),0.001);
        var combined = results.getMyPublishedRecords(identity);
        assertEquals(rows,combined.grades());
        assertEquals(1,combined.semesters().size());
        assertEquals(4.0,combined.semesters().getFirst().result().getCgpa(),0.001);
        StudentAccessTest.denied(() -> gpa.getCourseRows(4,9));
        var saved = semesters.getFirst().result();
        StudentAccessTest.denied(() -> gpa.saveSemesterResult(saved));
        StudentAccessTest.denied(() -> results.calculateAndSaveResult(100));
        StudentAccessTest.denied(() -> results.publishResult(10,null));
        assertEquals(2L,((Number)db.academic.scalar("SELECT COUNT(*) FROM semester_results")).longValue());
        db.academic.login(4,"STUDENT");
        var earlierLogin = identity;
        StudentAccessTest.denied(() -> results.getMyPublishedResults(earlierLogin));
        StudentAccessTest.denied(() -> results.getMyPublishedRecords(earlierLogin));
        assertEquals("B",results.getMyPublishedResults(SessionManager.requireSession()).getFirst().grade());
        assertEquals("B",results.getMyPublishedRecords(SessionManager.requireSession()).grades().getFirst().grade());
    }

    public void testPublishedSemesterChronologyRefreshAndHistoricalGradePoints() throws Exception {
        db.publishSamples();
        db.academic.login(3,"STUDENT");
        var results = new ResultService();
        assertEquals(1,results.getMyPublishedRecords(SessionManager.requireSession()).semesters().size());
        saveGpa();
        // Publish through the existing lecturer workflow; no production tables are written.
        db.academic.login(1,"LECTURER");
        new MarkService().saveMark(new lk.ac.ruhuna.ftms.model.Mark(200,20,60.0,1));
        db.academic.confirmedFixtureEligibility(200);
        results.publishResult(20,results.calculateAndSaveResult(200).publicationSnapshot());
        assertNull("Publication invalidates the saved cache",
                db.academic.scalar("SELECT cgpa FROM semester_results WHERE student_id=3 AND semester_id=1"));
        // Refresh must calculate from the new publication without requiring a lecturer GPA save.
        db.academic.login(3,"STUDENT");
        var view = results.getMyPublishedRecords(SessionManager.requireSession());
        assertEquals(2,view.grades().size());
        assertEquals(java.util.List.of(9,1),view.semesters().stream().map(s -> s.semester().semesterId()).toList());
        assertEquals(4.0,view.semesters().getFirst().result().getSgpa(),0.001);
        assertEquals(4.0,view.semesters().getFirst().result().getCgpa(),0.001);
        assertEquals(3.0,view.semesters().getLast().result().getSgpa(),0.001);
        assertEquals(3.6,view.semesters().getLast().result().getCgpa(),0.001);
        db.academic.execute("UPDATE grade_rules SET grade_point=0,is_pass=FALSE");
        assertEquals(3.6,results.getMyPublishedRecords(SessionManager.requireSession())
                .semesters().getLast().result().getCgpa(),0.001);
        assertEquals(2L,((Number)db.academic.scalar("SELECT COUNT(*) FROM semester_results")).longValue());
        assertNull("Student Refresh must not overwrite that cache",
                db.academic.scalar("SELECT cgpa FROM semester_results WHERE student_id=3 AND semester_id=1"));
    }

    public void testCompletedHistoricalEnrollmentsAndOfferingsRemainVisible() throws Exception {
        db.publishSamples();
        db.academic.execute("UPDATE enrollments SET status='COMPLETED' WHERE enrollment_id=100");
        db.academic.execute("UPDATE course_offerings SET status='COMPLETED' WHERE offering_id=10");
        db.academic.execute("UPDATE courses SET is_active=FALSE WHERE course_id=1");
        db.academic.execute("UPDATE semesters SET is_active=FALSE");
        db.academic.execute("UPDATE academic_years SET is_active=FALSE");
        db.academic.login(3,"STUDENT");
        var view=new ResultService().getMyPublishedRecords(SessionManager.requireSession());
        assertEquals(1,view.grades().size());
        assertEquals("TEST-A",view.grades().getFirst().courseCode());
        assertEquals(4.0,view.semesters().getFirst().result().getSgpa(),0.001);
    }

    public void testMissingProfileAndInvalidEnrollmentsHaveDistinctFeedback() throws Exception {
        db.academic.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE student_id=3");
        var results=new ResultService();
        assertEquals(ResultService.NO_VALID_ENROLLMENT,
                results.getMyPublishedRecords(SessionManager.requireSession()).emptyMessage());
        db.academic.execute("DELETE FROM enrollments WHERE student_id=3");
        assertEquals(ResultService.NO_VALID_ENROLLMENT,
                results.getMyPublishedRecords(SessionManager.requireSession()).emptyMessage());
        db.academic.execute("DELETE FROM students WHERE student_id=3");
        try { results.getMyPublishedRecords(SessionManager.requireSession()); fail("Missing profile accepted"); }
        catch(SecurityException expected) { assertTrue(expected.getMessage().contains("profile is missing")); }
    }

    public void testFailedResultsQueryPropagatesInsteadOfReturningEmptyRecords() throws Exception {
        var dao=new lk.ac.ruhuna.ftms.dao.CourseResultDAO() {
            @Override public java.util.List<PublishedStudentResult> findPublishedForStudent(java.sql.Connection c,int student)
                    throws java.sql.SQLException { throw new java.sql.SQLException("Fixture query failure","42S02"); }
        };
        var results=new ResultService(dao,new lk.ac.ruhuna.ftms.dao.GradeRuleDAO(),new lk.ac.ruhuna.ftms.dao.EligibilityDAO());
        try { results.getMyPublishedRecords(SessionManager.requireSession()); fail("Query failure swallowed"); }
        catch(java.sql.SQLException expected) { assertEquals("42S02",expected.getSQLState()); }
    }

    public void testPendingGpaForUnresolvedRepeatAndNoApplicablePublishedGrades() throws Exception {
        db.publishSamples();
        db.academic.login(3,"STUDENT");
        var results = new ResultService();
        db.academic.execute("UPDATE enrollments SET attempt_no=2 WHERE enrollment_id=100");
        var repeated = results.getMyPublishedRecords(SessionManager.requireSession());
        assertEquals(2,repeated.grades().getFirst().attempt());
        assertEquals("A",repeated.grades().getFirst().grade());
        assertNull(repeated.semesters().getFirst().result().getSgpa());
        assertNull(repeated.semesters().getFirst().result().getCgpa());
        db.academic.execute("UPDATE enrollments SET attempt_no=1 WHERE enrollment_id=100");
        db.academic.execute("UPDATE course_results SET grade_point=NULL,grade_letter=NULL,result_status='INCOMPLETE' WHERE enrollment_id=100");
        var pending = results.getMyPublishedRecords(SessionManager.requireSession());
        assertNull(pending.grades().getFirst().gradePoint());
        assertNull(pending.semesters().getFirst().result().getSgpa());
        assertNull(pending.semesters().getFirst().result().getCgpa());
        assertTrue(pending.semesters().getFirst().status().contains("SGPA Pending: no included published credits"));
    }

    public void testWithdrawnAndCancelledExcludedFromBothTables() throws Exception {
        db.publishSamples();
        db.academic.login(3,"STUDENT");
        var results = new ResultService();
        db.academic.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE enrollment_id=100");
        var withdrawn = results.getMyPublishedRecords(SessionManager.requireSession());
        assertTrue(withdrawn.grades().isEmpty());
        assertTrue(withdrawn.semesters().isEmpty());
        db.academic.execute("UPDATE enrollments SET status='ENROLLED' WHERE enrollment_id=100");
        db.academic.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=10");
        var cancelled = results.getMyPublishedRecords(SessionManager.requireSession());
        assertTrue(cancelled.grades().isEmpty());
        assertTrue(cancelled.semesters().isEmpty());
    }

    public void testSnapshotRequiresActiveDatabaseStudentAndSameLogin() throws Exception {
        db.publishSamples();
        db.academic.login(3,"STUDENT");
        db.academic.execute("UPDATE users SET is_active=FALSE WHERE user_id=3");
        StudentAccessTest.denied(() -> new ResultService().getMyPublishedRecords(SessionManager.requireSession()));
        db.academic.execute("UPDATE users SET is_active=TRUE WHERE user_id=3");
        var dao = new lk.ac.ruhuna.ftms.dao.CourseResultDAO() {
            @Override public java.util.List<PublishedStudentResult> findPublishedForStudent(java.sql.Connection c,int student)
                    throws java.sql.SQLException {
                var rows = super.findPublishedForStudent(c,student);
                db.academic.login(4,"STUDENT");
                return rows;
            }
        };
        var results = new ResultService(dao,new lk.ac.ruhuna.ftms.dao.GradeRuleDAO(),new lk.ac.ruhuna.ftms.dao.EligibilityDAO());
        StudentAccessTest.denied(() -> results.getMyPublishedRecords(SessionManager.requireSession()));
        assertTrue(db.academic.connection.getAutoCommit());
    }

    public void testRepeatableReadDoesNotDependOnDatabaseDefaultIsolation() throws Exception {
        db.publishSamples();
        saveGpa();
        db.academic.login(3,"STUDENT");
        db.academic.connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        var dao = new lk.ac.ruhuna.ftms.dao.CourseResultDAO() {
            @Override public java.util.List<PublishedStudentResult> findPublishedForStudent(java.sql.Connection c,int student)
                    throws java.sql.SQLException {
                assertEquals(3,student);
                assertFalse(c.getAutoCommit());
                assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());
                return super.findPublishedForStudent(c,student);
            }
        };
        var results = new ResultService(dao,new lk.ac.ruhuna.ftms.dao.GradeRuleDAO(),new lk.ac.ruhuna.ftms.dao.EligibilityDAO());
        int opened = db.academic.connectionsOpened();
        assertEquals(4.0,results.getMyPublishedRecords(SessionManager.requireSession())
                .semesters().getFirst().result().getCgpa(),0.001);
        assertEquals("Grades and GPA must share one connection",opened+1,db.academic.connectionsOpened());
        assertTrue(db.academic.connection.getAutoCommit());
    }

    private void saveGpa() throws Exception {
        db.academic.login(1,"LECTURER");
        var gpa = new GPAService();
        gpa.saveSemesterResult(gpa.calculateSemesterGPA(3,9));
        db.academic.login(3,"STUDENT");
    }
}
