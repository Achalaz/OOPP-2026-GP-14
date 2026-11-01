package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableInput;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.time.LocalTime;
import java.util.List;

/** Fast security checks: unauthorized requests must fail before opening a database connection. */
public class LecturerTimetableServiceTest extends TestCase {
    @Override protected void tearDown() { SessionManager.logout(); }

    public void testUnauthenticatedAndOtherRolesAreRejected() throws Exception {
        SessionManager.logout();
        denied(this::service);
        denied(() -> NavigationUtil.navigate(null, "/fxml/lecturer_timetable.fxml", "Forbidden"));
        for (String role : List.of("ADMIN", "STUDENT", "TECHNICAL_OFFICER")) {
            login(4, role);
            denied(this::service);
            denied(() -> NavigationUtil.navigate(null, "/fxml/lecturer_timetable.fxml", "Forbidden"));
        }
    }

    public void testLogoutRejectsExistingReader() throws Exception {
        login(4, "LECTURER");
        var service = service();
        SessionManager.logout();
        denied(service::getMyTimetable);
    }

    public void testSwitchingLecturerRejectsExistingReader() throws Exception {
        login(4, "LECTURER");
        var service = service();
        login(5, "LECTURER");
        denied(service::getMyTimetable);
    }

    public void testNewLoginForSameLecturerRejectsExistingReader() throws Exception {
        login(4, "LECTURER");
        var service = service();
        login(4, "LECTURER");
        denied(service::getMyTimetable);
    }

    public void testLecturerCannotUseAdminWritesOrScreen() throws Exception {
        login(1, "ADMIN");
        var admin = new AdminTimetableService(new TimetableDAO(), () -> {
            throw new AssertionError("Denied write reached the database");
        });
        login(4, "LECTURER");
        var identity = SessionManager.requireSession();
        var input = new TimetableInput(10, "MONDAY", LocalTime.of(9, 0), LocalTime.of(10, 0),
                "LT-1", "LECTURE", 4, null, null);
        denied(AdminTimetableService::new);
        denied(() -> admin.createTimetableEntry(input));
        denied(() -> admin.updateTimetableEntry(1, input));
        denied(() -> admin.deleteTimetableEntry(1));
        denied(() -> NavigationUtil.navigate(null, "/fxml/admin_timetable.fxml", "Forbidden"));
        assertTrue(SessionManager.isCurrentSession(identity));
    }

    public void testLecturerScreenBindingsAndReadOnlyControls() throws Exception {
        for (String name : List.of("lecturer_dashboard", "lecturer_timetable")) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (var resource = getClass().getResourceAsStream("/fxml/" + name + ".fxml")) {
                var document = factory.newDocumentBuilder().parse(resource);
                var controller = Class.forName(document.getDocumentElement().getAttribute("fx:controller"));
                var ids = new java.util.HashSet<String>();
                var actions = new java.util.HashSet<String>();
                for (var method : controller.getDeclaredMethods()) actions.add(method.getName());
                var nodes = document.getElementsByTagName("*");
                boolean timetableButton = false;
                for (int i = 0; i < nodes.getLength(); i++) {
                    var node = (org.w3c.dom.Element) nodes.item(i);
                    String id = node.getAttribute("fx:id"), action = node.getAttribute("onAction");
                    if (!id.isEmpty()) assertTrue("Duplicate ID: " + id, ids.add(id));
                    if (action.startsWith("#")) assertTrue(action, actions.contains(action.substring(1)));
                    if ("#handleMyTimetable".equals(action)) timetableButton = true;
                    if (name.equals("lecturer_timetable")) {
                        assertFalse("No user ID entry", node.getTagName().equals("TextField"));
                        if (node.getTagName().equals("TableView")) assertEquals("false", node.getAttribute("editable"));
                        if (!action.isEmpty()) assertTrue(List.of("#handleRefresh", "#handleBack").contains(action));
                    }
                }
                if (name.equals("lecturer_dashboard")) assertTrue("Dashboard timetable entry point", timetableButton);
                for (var field : controller.getDeclaredFields()) {
                    if (field.isAnnotationPresent(javafx.fxml.FXML.class)) assertTrue(field.getName(), ids.contains(field.getName()));
                }
            }
        }
    }

    private LecturerTimetableService service() {
        return new LecturerTimetableService(new TimetableDAO(), () -> {
            throw new AssertionError("Unauthorized request reached the database");
        });
    }

    private static void login(int id, String role) {
        SessionManager.startSession(new User(id, "test", "Test Lecturer", null, role, true));
    }

    interface Action { void run() throws Exception; }
    static void denied(Action action) throws Exception {
        try { action.run(); fail("Expected access denial"); }
        catch (SecurityException expected) { }
    }
}
