package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO;
import lk.ac.ruhuna.ftms.model.SemesterResult;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static lk.ac.ruhuna.ftms.service.StudentAccessTest.denied;

/** Real authorization, calculation and persistence against connection-local temporary tables only. */
public class GpaManagementMySqlIT extends TestCase {
    private StudentDatabaseFixture db;
    private final GPAService gpa = new GPAService();

    protected void setUp() throws Exception { db = new StudentDatabaseFixture(); }
    protected void tearDown() throws Exception { db.close(); }

    public void testAdminCanSelectAllStudentsAndSaveChronologicalWeightedHistory() throws Exception {
        db.publishSamples();
        db.academic.execute("INSERT INTO course_results(enrollment_id,grade_letter,grade_point,result_status,published) VALUES (300,'C',2,'PASS',TRUE)");
        db.academic.login(5,"ADMIN");
        var selection = gpa.getStudentSelection();
        assertEquals(List.of(3,4), selection.students().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        assertEquals(0, selection.blockedStudents());
        assertTrue(selection.missingAssignments().isEmpty());
        assertTrue(selection.message().contains("Admin GPA management"));
        assertEquals(List.of(9,1), gpa.getSemestersForStudent(4).stream().map(SemesterResultDAO.SemesterOption::semesterId).toList());
        var sources = academicSources();
        // Semester IDs deliberately run backwards. CGPA = (3*3 + 1*2)/4 = 2.75.
        var view = gpa.getSemesterView(4,1);
        assertEquals(2.0, view.result().getSgpa(), 0.00001);
        assertEquals(2.75, view.result().getCgpa(), 0.00001);
        assertFalse(view.savedCurrent());
        gpa.saveSemesterResult(new SemesterResult(4,1,999,999,999,99.0,99.0));
        var saved = new GPAService().getSemesterView(4,1);
        assertTrue(saved.savedCurrent());
        assertEquals(1.0, saved.result().getTotalRegisteredCredits(), 0.00001);
        assertEquals(2.0, saved.result().getSgpa(), 0.00001);
        assertEquals(2.75, saved.result().getCgpa(), 0.00001);
        assertEquals(3.0, gpa.getSemesterResult(4,9).getSgpa(), 0.00001);
        assertEquals(2, gpa.getSemesterResultsByStudent(4).size());
        assertEquals(2L, countSummaries());
        assertEquals("Saving GPA must not change marks, results, assignments or enrollments", sources, academicSources());
    }

    public void testAdminExcludesIncompleteResultsAndIncludesPublishedZeroFail() throws Exception {
        db.publishSamples();
        db.academic.execute("UPDATE course_results SET grade_letter='F',grade_point=0,result_status='FAIL' WHERE enrollment_id=101");
        db.academic.execute("INSERT INTO course_results(enrollment_id,grade_letter,grade_point,result_status,published) VALUES (300,'A',4,'INCOMPLETE',TRUE)");
        db.academic.login(5,"ADMIN");
        var view = gpa.getSemesterView(4,1);
        assertNull(view.result().getSgpa());
        assertEquals(0.0, view.result().getCgpa(), 0.00001);
        assertEquals(0.0, view.includedCredits(), 0.00001);
        assertEquals(1, view.excludedCourses());
        assertEquals(0.0, view.result().getTotalQualityPoints(), 0.00001);
        gpa.saveSemesterResult(view.result());
        assertNull(new SemesterResultDAO().getByStudentAndSemester(4,1).getSgpa());
        assertEquals(0.0, gpa.getSemesterResult(4,9).getTotalEarnedCredits(), 0.00001);
    }

    public void testAdminDoesNotInventGradesForDraftOrMissingResults() throws Exception {
        db.academic.login(5,"ADMIN");
        assertNull(gpa.calculateSemesterGPA(3,9).getSgpa());
        assertNull(gpa.calculateCGPA(3,1));
        db.publishSamples();
        db.academic.login(5,"ADMIN");
        var view = gpa.getSemesterView(3,1);
        assertNull(view.rows().getFirst().gradePoint());
        assertNull(view.result().getSgpa());
        assertEquals(4.0, view.result().getCgpa(), 0.00001);
        assertEquals(0.0, view.includedCredits(), 0.00001);
        gpa.saveSemesterResult(view.result());
        assertNull(new SemesterResultDAO().getByStudentAndSemester(3,1).getSgpa());
        assertEquals(0L, ((Number)db.academic.scalar("SELECT COUNT(*) FROM course_results WHERE enrollment_id=200 AND published=TRUE")).longValue());
    }

    public void testAdminAccessRequiresCurrentActiveDatabaseRoleAndExistingStudent() throws Exception {
        db.academic.login(5,"ADMIN");
        denied(() -> gpa.getSemesterView(999,9));
        denied(() -> gpa.saveSemesterResult(new SemesterResult(999,9,0,0,0,0.0,0.0)));
        try {
            gpa.saveSemesterResult(new SemesterResult(3,999,0,0,0,0.0,0.0));
            fail("Unknown semester accepted");
        } catch (IllegalArgumentException expected) { }
        db.academic.execute("UPDATE users SET is_active=FALSE WHERE user_id=5");
        denied(gpa::getStudents);
        denied(() -> gpa.calculateSemesterGPA(3,9));
        denied(() -> gpa.saveSemesterResult(new SemesterResult(3,9,0,0,0,0.0,0.0)));
        db.academic.execute("UPDATE users SET is_active=TRUE,role='TECHNICAL_OFFICER' WHERE user_id=5");
        denied(gpa::getStudentSelection);
        db.academic.login(1,"ADMIN"); // Forged session role must not bypass the database actor check.
        denied(() -> gpa.getCourseRows(4,9));
        db.academic.login(6,"TECHNICAL_OFFICER");
        denied(() -> gpa.getSemesterView(3,9));
        denied(() -> gpa.saveSemesterResult(new SemesterResult(3,9,0,0,0,0.0,0.0)));
        SessionManager.logout();
        denied(gpa::getStudents);
        assertEquals(0L, countSummaries());
    }

    public void testAdminSaveRollsBackOnFailureAndSessionReplacement() throws Exception {
        db.publishSamples();
        db.academic.login(5,"ADMIN");
        var requested = gpa.calculateSemesterGPA(4,9);
        db.academic.failAfterWrite("INSERT INTO semester_results");
        try { gpa.saveSemesterResult(requested); fail("Injected failure ignored"); }
        catch (SQLException expected) { }
        assertEquals(0L, countSummaries());
        db.academic.afterWrite(() -> db.academic.login(3,"STUDENT"));
        denied(() -> gpa.saveSemesterResult(requested));
        assertEquals(0L, countSummaries());
    }

    public void testLecturerPartialScopeAndRevocationCannotReadOrSaveFullGpa() throws Exception {
        db.publishSamples();
        db.academic.login(2,"LECTURER");
        var selection = gpa.getStudentSelection();
        assertTrue(selection.students().isEmpty());
        assertTrue(selection.message().contains("Complete transcript access is unavailable"));
        assertTrue(selection.message().contains("Admin dashboard"));
        assertTrue(selection.message().contains("Assigned Course Reports"));
        assertFalse(selection.toString().contains("TEST/4"));
        denied(() -> gpa.getSemesterView(4,9));
        denied(() -> gpa.getSemesterResultsByStudent(4));
        denied(() -> gpa.getCourseRows(4,9));
        denied(() -> gpa.calculateCGPA(4,1));
        denied(() -> gpa.saveSemesterResult(new SemesterResult(4,1,0,0,0,0.0,0.0)));
        assertEquals(List.of(30), new ReportService().getCourseOfferings().stream().map(r -> r.offeringId()).toList());
        db.academic.login(1,"LECTURER");
        var requested = gpa.calculateSemesterGPA(3,9);
        db.academic.execute("DELETE FROM lecturer_course_assignments WHERE lecturer_id=1 AND offering_id=20");
        denied(() -> gpa.saveSemesterResult(requested));
        denied(() -> gpa.getSemesterView(3,9));
        assertEquals(0L, countSummaries());
    }

    public void testStudentSeesOnlyOwnPublishedGpaAndCannotSave() throws Exception {
        db.publishSamples();
        db.academic.execute("INSERT INTO semester_results(student_id,semester_id,sgpa,cgpa) VALUES (3,9,1,1),(3,1,2,2)");
        db.academic.login(3,"STUDENT");
        assertEquals(List.of(3), gpa.getStudents().stream().map(SemesterResultDAO.StudentOption::studentId).toList());
        var published = gpa.getMyPublishedGpa(SessionManager.requireSession());
        assertEquals(List.of(9), published.stream().map(s -> s.semester().semesterId()).toList());
        assertEquals(4.0, published.getFirst().result().getSgpa(), 0.00001);
        assertEquals(4.0, published.getFirst().result().getCgpa(), 0.00001);
        assertFalse(gpa.getSemesterView(3,9).savedCurrent());
        assertNull(gpa.getSemesterView(3,1).result().getSgpa());
        assertNull(gpa.getCourseRows(3,1).getFirst().gradePoint());
        denied(() -> gpa.getSemestersForStudent(4));
        denied(() -> gpa.getSemesterView(4,9));
        denied(() -> gpa.getCourseRows(4,9));
        denied(() -> gpa.getSemesterResultsByStudent(4));
        denied(() -> gpa.calculateCGPA(4,9));
        denied(() -> gpa.saveSemesterResult(published.getFirst().result()));
        denied(() -> gpa.saveSemesterResult(new SemesterResult(4,9,0,0,0,0.0,0.0)));
        assertEquals(2L, countSummaries());
        assertEquals("Student reads must not overwrite stale summaries", 1.0,
                ((Number)db.academic.scalar("SELECT sgpa FROM semester_results WHERE student_id=3 AND semester_id=9")).doubleValue(), 0.00001);
    }

    public void testAllAuthorizedRolesUseSamePublishedCalculationAndRepeatPolicy() throws Exception {
        db.publishSamples();
        for (var role : List.of("ADMIN","LECTURER","STUDENT")) {
            db.academic.login(role.equals("ADMIN") ? 5 : role.equals("LECTURER") ? 1 : 3, role);
            assertEquals(4.0, gpa.calculateSemesterGPA(3,9).getSgpa(), 0.00001);
            assertEquals(4.0, gpa.calculateCGPA(3,1), 0.00001);
        }
        db.academic.execute("UPDATE enrollments SET attempt_no=2 WHERE enrollment_id=100");
        for (var role : List.of("ADMIN","LECTURER","STUDENT")) {
            db.academic.login(role.equals("ADMIN") ? 5 : role.equals("LECTURER") ? 1 : 3, role);
            assertNull(gpa.calculateSemesterGPA(3,9).getSgpa());
            assertNull(gpa.calculateCGPA(3,1));
        }
    }

    private long countSummaries() throws SQLException {
        return ((Number)db.academic.scalar("SELECT COUNT(*) FROM semester_results")).longValue();
    }

    private List<List<String>> academicSources() throws SQLException {
        var snapshot = new ArrayList<List<String>>();
        for (var table : List.of("marks","course_results","lecturer_course_assignments","enrollments")) {
            try (var statement = db.academic.connection.createStatement();
                 var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                while (rows.next()) {
                    var row = new ArrayList<String>();
                    row.add(table);
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                    snapshot.add(row);
                }
            }
        }
        return snapshot;
    }
}
