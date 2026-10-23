package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.*;
import static lk.ac.ruhuna.ftms.service.EnrollmentMaterialsAccessTest.denied;

/** Explicit opt-in SELECT-only check of the supplied stu account; snapshots prove no data changes. */
public class EnrollmentMaterialsLiveMySqlIT extends TestCase {
    public void testExistingStudentMetadataAndRealManagedBytes() throws Exception {
        var before = snapshot();
        try {
            var user = new UserDAO().getAllUsers().stream().filter(u -> u.getUsername().equals("stu"))
                    .findFirst().orElseThrow();
            SessionManager.startSession(user);
            var courses = new StudentCourseService().getMyCourses();
            assertEquals(Set.of(3, 4, 5), courses.stream().map(c -> c.offeringId()).collect(java.util.stream.Collectors.toSet()));
            var materials = new StudentCourseMaterialService();
            for (var course : courses) {
                var rows = materials.list(course.offeringId());
                long databaseCount;
                try (var connection = DBConnection.getConnection();
                     var statement = connection.prepareStatement("SELECT COUNT(*) FROM course_materials WHERE offering_id=?")) {
                    statement.setInt(1, course.offeringId());
                    try (var result = statement.executeQuery()) { result.next(); databaseCount = result.getLong(1); }
                }
                assertEquals(databaseCount, rows.size());
                System.out.println("LIVE " + course.courseCode() + ": " + rows.size() + " material records");
                for (var row : rows) {
                    if (row.availability().equals("Available")) {
                        byte[] studentBytes = materials.download(course.offeringId(), row.materialId());
                        byte[] storedBytes = new MaterialStorage(MaterialStorage.configuredRoot()).read(row.resourcePath());
                        assertTrue(Arrays.equals(storedBytes, studentBytes));
                        System.out.println("LIVE managed material " + row.materialId() + ": verified " + studentBytes.length + " bytes");
                    } else {
                        try { materials.download(course.offeringId(), row.materialId()); fail("Unavailable legacy file downloaded"); }
                        catch (java.io.IOException error) { assertEquals(StudentCourseMaterialService.UNAVAILABLE, error.getMessage()); }
                    }
                }
            }
            denied(() -> materials.list(6)); denied(() -> materials.download(6, 7));
            denied(() -> materials.download(3, 7));
            SessionManager.logout(); denied(() -> materials.list(3));
        } finally {
            SessionManager.logout();
            assertEquals("Production enrollments and material metadata must be unchanged", before, snapshot());
        }
    }

    private List<List<String>> snapshot() throws Exception {
        var result = new ArrayList<List<String>>();
        try (var connection = DBConnection.getConnection()) {
            for (String table : List.of("enrollments", "course_materials")) {
                try (var statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY 1"); var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        var row = new ArrayList<String>(); row.add(table);
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                        result.add(row);
                    }
                }
            }
        }
        return result;
    }
}
