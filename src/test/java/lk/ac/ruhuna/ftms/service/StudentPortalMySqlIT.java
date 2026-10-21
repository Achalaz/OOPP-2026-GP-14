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
        StudentAccessTest.denied(() -> gpa.getCourseRows(4,9));
        StudentAccessTest.denied(() -> gpa.saveSemesterResult(semesters.getFirst().result()));
        StudentAccessTest.denied(() -> results.calculateAndSaveResult(100));
        StudentAccessTest.denied(() -> results.publishResult(10,null));
        assertEquals(0L,((Number)db.academic.scalar("SELECT COUNT(*) FROM semester_results")).longValue());
        db.academic.login(4,"STUDENT");
        StudentAccessTest.denied(() -> results.getMyPublishedResults(identity));
        assertEquals("B",results.getMyPublishedResults(SessionManager.requireSession()).getFirst().grade());
    }
}
