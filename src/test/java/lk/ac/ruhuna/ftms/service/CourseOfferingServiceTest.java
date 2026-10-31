package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.List;

public class CourseOfferingServiceTest extends TestCase {
    private final RecordingDAO dao = new RecordingDAO();
    private final CourseOfferingService service = new CourseOfferingService(dao, CourseService::isCurrentUserAdmin);

    @Override protected void tearDown() { SessionManager.logout(); }

    public void testNavigationRejectsUnauthorizedUsersBeforeLoadingFxml() throws Exception {
        SessionManager.logout();
        expect(SecurityException.class, () -> NavigationUtil.navigate(
                null, "/fxml/course_offerings.fxml", "Course Offerings"));
        for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
            login(role);
            expect(SecurityException.class, () -> NavigationUtil.navigate(
                    null, "/fxml/course_offerings.fxml", "Course Offerings"));
        }
    }

    public void testEveryOperationRequiresAnActiveAdminSession() throws Exception {
        SessionManager.logout();
        denied();
        for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
            login(role);
            denied();
        }
        SessionManager.logout();
        expect(IllegalArgumentException.class, () -> SessionManager.startSession(
                new User(1, "test", "Test", "", "ADMIN", false)));
        denied();
        login("ADMIN");
        assertEquals(1, service.getAllOfferings().size());
        SessionManager.logout();
        denied(); // The same service must recheck authorization after logout.
        login("STUDENT");
        denied();
    }

    public void testAdminCrudAndDropdownDelegation() throws Exception {
        login("ADMIN");
        assertEquals(1, service.getAllOfferings().size());
        assertEquals(1, service.getActiveCourses().size());
        assertEquals(1, service.getSemesters().size());
        CourseOffering offering = offering();
        assertTrue(service.saveOffering(offering));
        assertSame(offering, dao.saved);
        offering.setOfferingId(20);
        assertTrue(service.saveOffering(offering));
        assertTrue(service.deleteOffering(20));
        assertEquals(20, dao.deleted);
    }

    public void testSearchTrimsAndBlankSearchLoadsAll() throws Exception {
        login("ADMIN");
        service.searchOfferings("  ICT  ");
        assertEquals("ICT", dao.keyword);
        service.searchOfferings(null);
        service.searchOfferings("   ");
        assertEquals(2, dao.reads);
    }

    public void testInvalidDetailsRejectedWithoutDatabaseAccess() throws Exception {
        login("ADMIN");
        expect(IllegalArgumentException.class, () -> service.saveOffering(null));
        expect(IllegalArgumentException.class, () -> service.deleteOffering(0));
        var validating = new CourseOfferingService(new CourseOfferingDAO(), CourseService::isCurrentUserAdmin);
        for (String status : new String[]{null, "CANCELED", "INVALID", "active"}) {
            CourseOffering invalid = offering(); invalid.setStatus(status);
            expect(IllegalArgumentException.class, () -> validating.saveOffering(invalid));
        }
        for (String group : new String[]{null, " ", "X".repeat(31)}) {
            CourseOffering invalid = offering(); invalid.setOfferingGroup(group);
            expect(IllegalArgumentException.class, () -> validating.saveOffering(invalid));
        }
        CourseOffering invalid = offering(); invalid.setCourseId(0);
        expect(IllegalArgumentException.class, () -> validating.saveOffering(invalid));
        invalid.setCourseId(1); invalid.setSemesterId(0);
        expect(IllegalArgumentException.class, () -> validating.saveOffering(invalid));
        assertEquals(0, dao.calls);
    }

    public void testDuplicateAndForeignKeyErrorsArePreserved() throws Exception {
        login("ADMIN");
        for (int code : List.of(1062, 1451, 1452)) {
            dao.error = new SQLException("constraint", "23000", code);
            try { service.saveOffering(offering()); fail("Expected constraint failure"); }
            catch (SQLException error) { assertSame(dao.error, error); }
        }
    }

    private void denied() throws Exception {
        int calls = dao.calls;
        expect(SecurityException.class, service::getAllOfferings);
        expect(SecurityException.class, () -> service.searchOfferings("ICT"));
        expect(SecurityException.class, () -> service.saveOffering(offering()));
        CourseOffering existing = offering(); existing.setOfferingId(20);
        expect(SecurityException.class, () -> service.saveOffering(existing));
        expect(SecurityException.class, () -> service.deleteOffering(20));
        expect(SecurityException.class, service::getActiveCourses);
        expect(SecurityException.class, service::getSemesters);
        assertEquals("Unauthorized operations must not reach the DAO", calls, dao.calls);
    }

    private static void login(String role) {
        SessionManager.startSession(new User(1, "test", "Test", "", role, true));
    }

    private static CourseOffering offering() {
        CourseOffering o = new CourseOffering(); o.setCourseId(1); o.setSemesterId(2);
        o.setOfferingGroup("A"); o.setStatus("PLANNED"); return o;
    }

    private interface Work { void run() throws Exception; }
    private static void expect(Class<? extends Exception> type, Work work) throws Exception {
        try { work.run(); } catch (Exception error) { if (type.isInstance(error)) return; throw error; }
        fail("Expected " + type.getSimpleName());
    }

    private static final class RecordingDAO extends CourseOfferingDAO {
        int calls, reads, deleted;
        String keyword;
        CourseOffering saved;
        SQLException error;
        @Override public List<CourseOffering> getAllOfferings() { calls++; reads++; return List.of(offering()); }
        @Override public List<CourseOffering> searchOfferings(String text) { calls++; keyword = text; return List.of(offering()); }
        @Override public List<CourseOption> getActiveCourses() { calls++; return List.of(new CourseOption(1, "ICT", "Computing", 3)); }
        @Override public List<SemesterOption> getSemesters() { calls++; return List.of(new SemesterOption(2, "Semester 2")); }
        @Override public boolean saveOffering(CourseOffering o) throws SQLException { calls++; if (error != null) throw error; saved = o; return true; }
        @Override public boolean deleteOffering(int id) { calls++; deleted = id; return true; }
    }
}
