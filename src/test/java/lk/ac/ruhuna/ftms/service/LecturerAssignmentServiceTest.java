package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.List;

public class LecturerAssignmentServiceTest extends TestCase {
    @Override protected void tearDown() { SessionManager.logout(); }

    public void testOnlyActiveAdminCanCreateService() {
        SessionManager.logout();
        expect(SecurityException.class, () -> new LecturerAssignmentService(new RecordingDAO()));
        for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
            login(role); expect(SecurityException.class, () -> new LecturerAssignmentService(new RecordingDAO()));
        }
        SessionManager.logout();
        expect(IllegalArgumentException.class, () -> SessionManager.startSession(new User(1,"fixture","Fixture","","ADMIN",false)));
    }

    public void testEveryOperationRechecksSessionAndRole() {
        login("ADMIN"); var dao = new RecordingDAO(); var service = new LecturerAssignmentService(dao);
        SessionManager.logout(); denied(service);
        for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER", "ADMIN")) {
            login(role); denied(service);
        }
        assertEquals(0, dao.calls);
    }

    public void testValidOperationsDelegateWithCurrentIdentity() throws Exception {
        login("ADMIN"); var dao = new RecordingDAO(); var service = new LecturerAssignmentService(dao);
        service.getEligibleLecturers(); service.getAssignmentsByOffering(10);
        for (AssignmentType type : AssignmentType.values()) {
            assertEquals(99, service.assignLecturer(10, 2, type));
            service.updateAssignmentType(10, 99, type);
        }
        assertNull(service.getAssignmentChangeBlocker(10,99)); service.removeAssignment(10,99);
        assertEquals(10, dao.calls); assertEquals(SessionManager.requireSession(), dao.identity);
    }

    public void testInvalidInputNeverReachesDao() {
        login("ADMIN"); var dao = new RecordingDAO(); var service = new LecturerAssignmentService(dao);
        expect(IllegalArgumentException.class, () -> service.getAssignmentsByOffering(0));
        expect(IllegalArgumentException.class, () -> service.assignLecturer(0,2,AssignmentType.PRIMARY));
        expect(IllegalArgumentException.class, () -> service.assignLecturer(10,0,AssignmentType.PRIMARY));
        expect(IllegalArgumentException.class, () -> service.assignLecturer(10,2,null));
        expect(IllegalArgumentException.class, () -> service.updateAssignmentType(10,0,AssignmentType.ASSISTANT));
        expect(IllegalArgumentException.class, () -> service.updateAssignmentType(10,99,null));
        expect(IllegalArgumentException.class, () -> service.removeAssignment(10,0));
        expect(IllegalArgumentException.class, () -> service.getAssignmentChangeBlocker(-1,99));
        assertEquals(0, dao.calls);
    }

    public void testDirectDaoAdminMethodsRejectWrongRoleBeforeOpeningDatabase() {
        for (String role : List.of("LECTURER", "STUDENT", "TECHNICAL_OFFICER")) {
            login(role); var dao = new LecturerCourseAssignmentDAO(); var identity = SessionManager.requireSession();
            expect(SecurityException.class, () -> dao.getEligibleLecturers(identity));
            expect(SecurityException.class, () -> dao.getAssignmentsByOffering(10, identity));
            expect(SecurityException.class, () -> dao.assignLecturer(10,2,AssignmentType.PRIMARY,identity));
            expect(SecurityException.class, () -> dao.updateAssignmentType(10,99,AssignmentType.ASSISTANT,identity));
            expect(SecurityException.class, () -> dao.removeAssignment(10,99,identity));
            expect(SecurityException.class, () -> dao.getAssignmentChangeBlocker(10,99,identity));
        }
    }

    private void denied(LecturerAssignmentService s) {
        expect(SecurityException.class, s::getEligibleLecturers);
        expect(SecurityException.class, () -> s.getAssignmentsByOffering(10));
        expect(SecurityException.class, () -> s.assignLecturer(10,2,AssignmentType.PRIMARY));
        expect(SecurityException.class, () -> s.updateAssignmentType(10,99,AssignmentType.PRACTICAL));
        expect(SecurityException.class, () -> s.removeAssignment(10,99));
        expect(SecurityException.class, () -> s.getAssignmentChangeBlocker(10,99));
    }
    private static void login(String role) { SessionManager.startSession(new User(1,"fixture","Fixture","",role,true)); }
    private interface Work { void run() throws Exception; }
    private static void expect(Class<? extends Exception> type, Work work) {
        try { work.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw new AssertionError(e); }
        fail("Expected " + type.getSimpleName());
    }
    private static class RecordingDAO extends LecturerCourseAssignmentDAO {
        int calls; SessionManager.SessionIdentity identity;
        private void record(SessionManager.SessionIdentity value) { calls++; identity=value; }
        @Override public List<LecturerOption> getEligibleLecturers(SessionManager.SessionIdentity i) { record(i); return List.of(); }
        @Override public List<LecturerCourseAssignment> getAssignmentsByOffering(int o,SessionManager.SessionIdentity i) { record(i); return List.of(); }
        @Override public int assignLecturer(int o,int l,AssignmentType t,SessionManager.SessionIdentity i) { record(i); return 99; }
        @Override public void updateAssignmentType(int o,int a,AssignmentType t,SessionManager.SessionIdentity i) { record(i); }
        @Override public void removeAssignment(int o,int a,SessionManager.SessionIdentity i) { record(i); }
        @Override public String getAssignmentChangeBlocker(int o,int a,SessionManager.SessionIdentity i) { record(i); return null; }
    }
}
