package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import java.util.List;

public class StudentAccessTest extends TestCase {
    protected void tearDown() { SessionManager.logout(); }

    public void testUnauthenticatedWrongRoleAndStaleLogin() throws Exception {
        SessionManager.logout();
        denied(StudentAccessService::new);
        denied(() -> NavigationUtil.navigate(null,"/fxml/student_results.fxml","Forbidden"));
        for (String role : List.of("ADMIN","LECTURER","TECHNICAL_OFFICER")) {
            login(3,role);
            denied(StudentAccessService::new);
            denied(() -> new ResultService().getMyPublishedRecords(SessionManager.requireSession()));
            denied(() -> NavigationUtil.navigate(null,"/fxml/student_results.fxml","Forbidden"));
        }
        login(3,"STUDENT");
        var reader = new StudentCourseService();
        var identity = SessionManager.requireSession();
        login(3,"STUDENT");
        denied(reader::getMyCourses);
        denied(() -> new ResultService().getMyPublishedResults(identity));
        denied(() -> new ResultService().getMyPublishedRecords(identity));
        denied(() -> new GPAService().getMyPublishedGpa(identity));
        denied(() -> new MedicalService().getMyMedicals(identity));
    }

    public void testStudentCannotEnterManagementOrWriteRecords() throws Exception {
        login(3,"STUDENT");
        // Legacy academic routes remain allowed by NavigationUtil; their service guards enforce scope.
        // StudentPortalMySqlIT verifies own published results/GPA and rejects cross-student reads/writes.
        for (String resource : List.of("admin_dashboard","users","notices","medical","attendance",
                "marks_entry","lecturer_dashboard","technical_officer_dashboard"))
            denied(() -> NavigationUtil.navigate(null,"/fxml/"+resource+".fxml","Forbidden"));
        var medical = new MedicalService();
        denied(() -> medical.submitMedical(null));
        denied(() -> medical.updatePendingMedical(null));
        denied(() -> medical.approveMedical(1,3,""));
        denied(() -> medical.rejectMedical(1,3,""));
        denied(() -> medical.deletePendingMedical(1,3));
        denied(medical::getAllMedicals);
        denied(() -> medical.getMedicalById(1));
        denied(() -> medical.getMedicalsByStudent(4));
        var attendance = new AttendanceService();
        denied(() -> attendance.saveAttendance(null));
        denied(() -> attendance.saveSession(null));
        denied(() -> attendance.getAttendanceByEnrollment(101));
        denied(() -> attendance.getAttendanceBySession(1));
    }

    public void testStudentFxmlFieldsAndActions() throws Exception {
        for (String name : List.of("student_dashboard","student_attendance","student_medical","student_courses",
                "student_results","profile","view_notices")) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var document = factory.newDocumentBuilder().parse(getClass().getResourceAsStream("/fxml/"+name+".fxml"));
            var controller = Class.forName(document.getDocumentElement().getAttribute("fx:controller"));
            var ids = new java.util.HashSet<String>();
            var methods = new java.util.HashSet<String>();
            for (var method : controller.getDeclaredMethods()) methods.add(method.getName());
            var nodes = document.getElementsByTagName("*");
            for (int i=0;i<nodes.getLength();i++) {
                var node = (org.w3c.dom.Element)nodes.item(i);
                String id=node.getAttribute("fx:id"), action=node.getAttribute("onAction");
                if (!id.isEmpty()) assertTrue("Duplicate fx:id in "+name,ids.add(id));
                if (action.startsWith("#")) assertTrue(name+" "+action,methods.contains(action.substring(1)));
            }
            for (var field : controller.getDeclaredFields())
                if (field.isAnnotationPresent(javafx.fxml.FXML.class))
                    assertTrue(name+" missing field "+field.getName(),ids.contains(field.getName()));
        }
    }

    private static void login(int id,String role) { SessionManager.startSession(new User(id,"test","Test",null,role,true)); }
    interface Action { void run() throws Exception; }
    static void denied(Action action) throws Exception {
        try { action.run(); fail("Expected a security rejection"); }
        catch (SecurityException expected) { }
    }
}
