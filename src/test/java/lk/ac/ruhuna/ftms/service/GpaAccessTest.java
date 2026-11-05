package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.controller.GPAController;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import static lk.ac.ruhuna.ftms.service.StudentAccessTest.denied;

public class GpaAccessTest extends TestCase {
    protected void tearDown() { SessionManager.logout(); }

    public void testGpaScreenRejectsAnonymousInactiveAndTechnicalOfficer() throws Exception {
        SessionManager.logout();
        denied(() -> NavigationUtil.navigate(null,"/fxml/gpa.fxml","Forbidden"));
        denied(GPAController::new);
        try {
            SessionManager.startSession(new User(5,"test","Test",null,"ADMIN",false));
            fail("Inactive Admin session accepted");
        } catch (IllegalArgumentException expected) { }
        denied(() -> NavigationUtil.navigate(null,"/fxml/gpa.fxml","Forbidden"));
        SessionManager.startSession(new User(6,"test","Test",null,"TECHNICAL_OFFICER",true));
        denied(() -> NavigationUtil.navigate(null,"/fxml/gpa.fxml","Forbidden"));
    }

    public void testAdminGpaAccessDoesNotGrantLecturerReportsOrResultManagement() throws Exception {
        SessionManager.startSession(new User(5,"test","Test",null,"ADMIN",true));
        denied(() -> NavigationUtil.navigate(null,"/fxml/reports.fxml","Forbidden"));
        denied(() -> NavigationUtil.navigate(null,"/fxml/results.fxml","Forbidden"));
        denied(() -> NavigationUtil.navigate(null,"/fxml/marks_entry.fxml","Forbidden"));
        assertEquals("/fxml/admin_dashboard.fxml", NavigationUtil.dashboardResource());
    }
}
