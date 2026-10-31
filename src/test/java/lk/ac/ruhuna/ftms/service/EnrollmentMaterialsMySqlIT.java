package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import static lk.ac.ruhuna.ftms.service.EnrollmentMaterialsAccessTest.denied;

/** Explicit opt-in MySQL integration. All writes use connection-local copies of live DDL.
 * TEMPORARY tables preserve enums, checks and unique keys; MySQL excludes foreign keys on these copies.
 * Production foreign keys are inspected separately, and production data is never written here. */
public class EnrollmentMaterialsMySqlIT extends TestCase {
    private AcademicDatabaseFixture db;
    private Path temp;
    private String oldStorage;

    protected void setUp() throws Exception {
        db = new AcademicDatabaseFixture();
        String ddl;
        try (var statement = db.connection.createStatement(); var rows = statement.executeQuery("SHOW CREATE TABLE course_materials")) {
            rows.next();
            ddl = rows.getString(2).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R", "")
                    .replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
        }
        db.execute(ddl);
        db.execute("INSERT INTO course_materials(material_id,offering_id,title,material_type,resource_path,uploaded_by) VALUES "
                + "(1,10,'Legacy note','LECTURE_NOTE','materials/oop/intro.pdf',1),"
                + "(2,10,'Practical','PRACTICAL','materials/oop/practical.pdf',1),"
                + "(3,10,'Assignment','ASSIGNMENT','materials/oop/assignment.pdf',1),"
                + "(4,20,'SQL','LECTURE_NOTE','materials/db/sql-intro.pdf',1),"
                + "(5,20,'Normalization','PRACTICAL','materials/db/normalization.pdf',1),"
                + "(6,30,'Other offering','OTHER','materials/other.pdf',2)");
        oldStorage = System.getProperty("ftms.materials.dir");
        temp = Files.createTempDirectory(Path.of("target"), "enrollment-materials-mysql-").toAbsolutePath();
        System.setProperty("ftms.materials.dir", temp.resolve("managed").toString());
    }
    protected void tearDown() throws Exception {
        try { if (db != null) db.close(); }
        finally {
            if (oldStorage == null) System.clearProperty("ftms.materials.dir"); else System.setProperty("ftms.materials.dir", oldStorage);
            if (temp != null) try (var files = Files.walk(temp)) {
                for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    public void testEnrollmentSearchCreateDuplicateAndNewStudentAccess() throws Exception {
        db.login(5, "ADMIN"); var admin = new AdminEnrollmentService();
        assertEquals(2, admin.findEligibleStudents("Test Fixture").size());
        assertEquals(1, admin.findEligibleStudents("academic-test-3").size());
        assertEquals(1, admin.findEligibleStudents("TEST/3").size());
        assertTrue(admin.findEligibleStudents("' OR 1=1 --").isEmpty());
        assertEquals(1, admin.findAvailableOfferings("TEST-C").size());
        int id = admin.enrollStudent(3, 30, 1);
        assertEquals("ENROLLED", admin.findEnrollmentById(id).status());
        invalid(() -> admin.enrollStudent(3, 30, 1), "already");
        int repeat = admin.enrollStudent(3, 30, 2);
        assertEquals("REPEAT", admin.findEnrollmentById(repeat).status());
        assertEquals(6, admin.findAllEnrollments().size());
        assertEquals(2, admin.searchEnrollments("TEST-C").stream().filter(e -> e.studentId() == 3).count());
        db.login(3, "STUDENT");
        assertTrue(new StudentCourseService().getMyCourses().stream().anyMatch(c -> c.enrollmentId() == id));
        assertEquals(1, new StudentCourseMaterialService().list(30).size());
    }

    public void testInvalidStudentProfileOfferingAttemptAndStatus() throws Exception {
        db.login(5, "ADMIN"); var admin = new AdminEnrollmentService();
        invalid(() -> admin.enrollStudent(999999, 30, 1), "Student");
        invalid(() -> admin.enrollStudent(1, 30, 1), "Student");
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=4");
        invalid(() -> admin.enrollStudent(4, 20, 1), "Student");
        db.execute("UPDATE users SET role='STUDENT' WHERE user_id=6");
        invalid(() -> admin.enrollStudent(6, 20, 1), "profile is missing");
        assertEquals(0L, ((Number) db.scalar("SELECT COUNT(*) FROM students WHERE student_id=6")).longValue());
        invalid(() -> admin.enrollStudent(3, 30, 0), "positive");
        invalid(() -> admin.enrollStudent(3, 999999, 1), "existing course offering");
        for (String state : List.of("CANCELLED", "COMPLETED")) {
            db.execute("UPDATE course_offerings SET status='" + state + "' WHERE offering_id=30");
            invalid(() -> admin.enrollStudent(3, 30, 1), "planned or active");
            assertTrue(admin.findAvailableOfferings("TEST-C").isEmpty());
        }
        db.execute("UPDATE course_offerings SET status='PLANNED' WHERE offering_id=30");
        int id = admin.enrollStudent(3, 30, 1);
        invalid(() -> admin.updateEnrollmentStatus(id, "ENROLLED", "DELETED"), "valid enrollment status");
        admin.updateEnrollmentStatus(id, "ENROLLED", "WITHDRAWN");
        invalid(() -> admin.updateEnrollmentStatus(id, "ENROLLED", "REPEAT"), "changed since");
        admin.updateEnrollmentStatus(id, "WITHDRAWN", "REPEAT");
        db.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=30");
        invalid(() -> admin.updateEnrollmentStatus(id, "REPEAT", "COMPLETED"), "only permit withdrawal");
        admin.updateEnrollmentStatus(id, "REPEAT", "WITHDRAWN");
    }

    public void testAdminDatabaseRevocationAndTransactionRollback() throws Exception {
        db.login(5, "ADMIN"); var admin = new AdminEnrollmentService();
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=5");
        denied(() -> admin.enrollStudent(3, 30, 1));
        denied(() -> admin.updateEnrollmentStatus(100, "ENROLLED", "WITHDRAWN"));
        db.execute("UPDATE users SET is_active=TRUE,role='STUDENT' WHERE user_id=5");
        denied(admin::findAllEnrollments);
        db.execute("UPDATE users SET role='ADMIN' WHERE user_id=5");
        db.failAfterWrite("INSERT INTO enrollments");
        try { admin.enrollStudent(3, 30, 1); fail("Failure expected"); } catch (SQLException expected) { }
        assertEquals(4L, ((Number) db.scalar("SELECT COUNT(*) FROM enrollments")).longValue());
        db.afterWrite(SessionManager::logout);
        denied(() -> admin.enrollStudent(3, 30, 1));
        assertEquals(4L, ((Number) db.scalar("SELECT COUNT(*) FROM enrollments")).longValue());
        db.login(5, "ADMIN"); var newAdmin = new AdminEnrollmentService();
        db.failAfterWrite("UPDATE enrollments");
        try { newAdmin.updateEnrollmentStatus(100, "ENROLLED", "WITHDRAWN"); fail("Failure expected"); }
        catch (SQLException expected) { }
        assertEquals("ENROLLED", db.scalar("SELECT status FROM enrollments WHERE enrollment_id=100"));
    }

    public void testWithdrawalPreservesAcademicRecordsAndRevokesMaterials() throws Exception {
        db.execute("INSERT INTO marks(enrollment_id,assessment_id,marks_obtained,entered_by) VALUES(100,10,25,1)");
        db.confirmedFixtureEligibility(100);
        db.execute("INSERT INTO course_results(enrollment_id,grade_letter,grade_point,result_status,published) VALUES(100,'B',3,'PASS',TRUE)");
        var before = academicSnapshot();
        db.login(5, "ADMIN"); var admin = new AdminEnrollmentService();
        admin.updateEnrollmentStatus(100, "ENROLLED", "WITHDRAWN");
        assertEquals(before, academicSnapshot());
        assertNotNull(admin.findEnrollmentById(100));
        db.login(3, "STUDENT"); var student = new StudentCourseMaterialService();
        denied(() -> student.list(10)); denied(() -> student.download(10, 1));
        db.login(5, "ADMIN");
        new AdminEnrollmentService().updateEnrollmentStatus(100, "WITHDRAWN", "ENROLLED");
        assertEquals(before, academicSnapshot());
    }

    public void testMetadataIsolationLegacyAvailabilityAndMaterialOwnership() throws Exception {
        db.login(3, "STUDENT"); var student = new StudentCourseMaterialService();
        assertEquals(3, student.list(10).size()); assertEquals(2, student.list(20).size());
        assertTrue(student.list(10).stream().allMatch(m -> StudentCourseMaterialService.UNAVAILABLE.equals(m.availability())));
        denied(() -> student.list(30)); denied(() -> student.download(30, 6));
        denied(() -> student.download(10, 6));
        try { student.download(10, 1); fail("Missing legacy file accepted"); }
        catch (java.io.IOException error) { assertEquals(StudentCourseMaterialService.UNAVAILABLE, error.getMessage()); }
        var lecturer = new CourseMaterialService();
        denied(() -> lecturer.save(10, 0, "Forbidden", "", "OTHER", Path.of("docs/SRS/OOP_project_group_14_SRS.pdf")));
        denied(() -> new CourseMaterialsService(new lk.ac.ruhuna.ftms.dao.CourseMaterialsDAO(),
                new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO()).getMyMaterials());
    }

    public void testRealPdfLecturerUploadStudentDownloadExportAndRevocation() throws Exception {
        // Use an existing real project PDF, never a synthetic placeholder in the user's materials.
        Path source = Path.of("docs/SRS/OOP_project_group_14_SRS.pdf");
        byte[] original = Files.readAllBytes(source);
        db.login(1, "LECTURER"); var lecturer = new CourseMaterialService();
        lecturer.save(10, 0, "Integration PDF", "Existing project document", "LECTURE_NOTE", source);
        var uploaded = lecturer.list(10).stream().filter(m -> m.title().equals("Integration PDF")).findFirst().orElseThrow();
        assertTrue(Arrays.equals(original, lecturer.download(10, uploaded.materialId())));
        db.login(3, "STUDENT"); var student = new StudentCourseMaterialService();
        assertEquals("Available", student.list(10).stream().filter(m -> m.materialId() == uploaded.materialId()).findFirst().orElseThrow().availability());
        assertTrue(Arrays.equals(original, student.download(10, uploaded.materialId())));
        Path destination = temp.resolve("download.pdf"); Files.writeString(destination, "Original destination");
        student.export(10, uploaded.materialId(), destination);
        assertTrue(Arrays.equals(original, Files.readAllBytes(destination)));
        db.execute("UPDATE enrollments SET status='WITHDRAWN' WHERE enrollment_id=100");
        denied(() -> student.export(10, uploaded.materialId(), destination));
        assertTrue(Arrays.equals(original, Files.readAllBytes(destination)));
        db.execute("UPDATE enrollments SET status='ENROLLED' WHERE enrollment_id=100");
        Files.delete(temp.resolve("managed").resolve(uploaded.resourcePath()));
        try { student.download(10, uploaded.materialId()); fail("Deleted file accepted"); }
        catch (java.io.IOException error) { assertEquals(StudentCourseMaterialService.UNAVAILABLE, error.getMessage()); }
        db.login(4, "STUDENT");
        denied(() -> new StudentCourseMaterialService().download(20, uploaded.materialId()));
    }

    public void testPermissionIsRecheckedOnEveryReadAndDownload() throws Exception {
        db.login(3, "STUDENT"); var student = new StudentCourseMaterialService();
        assertEquals(3, student.list(10).size());
        db.execute("UPDATE users SET is_active=FALSE WHERE user_id=3");
        denied(() -> student.list(10)); denied(() -> student.download(10, 1));
        db.execute("UPDATE users SET is_active=TRUE,role='LECTURER' WHERE user_id=3");
        denied(() -> student.list(10));
        db.execute("UPDATE users SET role='STUDENT' WHERE user_id=3");
        db.execute("UPDATE students SET student_id=33 WHERE student_id=3");
        denied(() -> student.list(10)); denied(() -> student.download(10, 1));
        db.execute("UPDATE students SET student_id=3 WHERE student_id=33");
        db.execute("UPDATE course_offerings SET status='CANCELLED' WHERE offering_id=10");
        denied(() -> student.list(10)); denied(() -> student.download(10, 1));
        db.execute("UPDATE course_offerings SET status='COMPLETED' WHERE offering_id=10");
        db.execute("UPDATE enrollments SET status='COMPLETED' WHERE enrollment_id=100");
        assertEquals(3, student.list(10).size());
        db.execute("UPDATE enrollments SET status='REPEAT' WHERE enrollment_id=100");
        assertEquals(3, student.list(10).size());
        SessionManager.logout(); denied(() -> student.list(10));
    }

    public void testAuthorizedOfferingWithNoMaterialRecords() throws Exception {
        db.execute("INSERT INTO course_offerings(offering_id,course_id,semester_id,offering_group,status) VALUES(40,1,9,'Empty','PLANNED')");
        db.login(5, "ADMIN");
        new AdminEnrollmentService().enrollStudent(3, 40, 1);
        db.login(3, "STUDENT");
        assertTrue(new StudentCourseMaterialService().list(40).isEmpty());
    }

    public void testMissingStorageAndMaliciousMetadataDoNotLeakPaths() throws Exception {
        db.login(3, "STUDENT"); var student = new StudentCourseMaterialService();
        for (String path : List.of("../secret.txt", "C:/private/secret.txt", UUID.randomUUID() + ".pdf")) {
            try (var statement = db.connection.prepareStatement("UPDATE course_materials SET resource_path=? WHERE material_id=1")) {
                statement.setString(1, path); statement.executeUpdate();
            }
            assertEquals(StudentCourseMaterialService.UNAVAILABLE, student.list(10).stream().filter(m -> m.materialId() == 1).findFirst().orElseThrow().availability());
            if (!MaterialStorage.managedName(path))
                assertNull(student.list(10).stream().filter(m -> m.materialId() == 1).findFirst().orElseThrow().resourcePath());
            try { student.download(10, 1); fail("Unavailable resource accepted"); }
            catch (java.io.IOException error) { assertEquals(StudentCourseMaterialService.UNAVAILABLE, error.getMessage()); }
        }
        System.setProperty("ftms.materials.dir", "invalid\u0000directory");
        assertEquals(3, new StudentCourseMaterialService().list(10).size());
    }

    private List<List<String>> academicSnapshot() throws SQLException {
        var result = new ArrayList<List<String>>();
        for (String table : List.of("marks", "attendance_records", "eligibility_records", "course_results")) {
            try (var statement = db.connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                while (rows.next()) {
                    var row = new ArrayList<String>(); row.add(table);
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                    result.add(row);
                }
            }
        }
        return result;
    }
    private static void invalid(EnrollmentMaterialsAccessTest.Action action, String message) throws Exception {
        try { action.run(); fail("Expected validation failure"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
}
