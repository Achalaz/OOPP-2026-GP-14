package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.controller.AdminTimetableController;
import lk.ac.ruhuna.ftms.dao.TimetableDAO;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableInput;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.TimetableTaskRunner;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TimetableValidationTest extends TestCase {
    @Override protected void tearDown() { SessionManager.logout(); }

    public void testStrict24HourTime() throws Exception {
        assertEquals(LocalTime.of(0, 0), parse("00:00"));
        assertEquals(LocalTime.of(23, 59), parse("23:59"));
        assertEquals(LocalTime.of(9, 5), parse(" 09:05 "));
        for (String text : List.of("24:00", "12:60", "9:00", "09:00 PM", "", "09:00:00", "-01:00")) {
            invalid(() -> parse(text));
        }
    }

    public void testInvalidOfferingDayTypeAndTimes() throws Exception {
        invalid(() -> validate(input(0, "MONDAY", "LECTURE", "09:00", "10:00", null, null)));
        invalid(() -> validate(input(1, "FUNDAY", "LECTURE", "09:00", "10:00", null, null)));
        invalid(() -> validate(input(1, "MONDAY", "EXAM", "09:00", "10:00", null, null)));
        invalid(() -> validate(input(1, "MONDAY", "LECTURE", "10:00", "10:00", null, null)));
        invalid(() -> validate(input(1, "MONDAY", "LECTURE", "11:00", "10:00", null, null)));
    }

    public void testEffectiveDatesAndOpenBounds() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 22);
        invalid(() -> validate(input(1, "MONDAY", "LECTURE", "09:00", "10:00", date, date.minusDays(1))));
        for (var range : List.of(new LocalDate[]{null, null}, new LocalDate[]{date, null},
                new LocalDate[]{null, date}, new LocalDate[]{date, date})) {
            validate(input(1, " monday ", " lecture ", "09:00", "10:00", range[0], range[1]));
        }
    }

    public void testRoomAndOptionalLecturer() throws Exception {
        var normalized = validate(new TimetableInput(1, " monday ", LocalTime.of(9,0), LocalTime.of(10,0),
                "  LT-1  ", " practical ", null, null, null));
        assertEquals("LT-1", normalized.room());
        assertEquals("MONDAY", normalized.dayOfWeek());
        assertEquals("PRACTICAL", normalized.sessionType());
        invalid(() -> validate(new TimetableInput(1,"MONDAY",LocalTime.of(9,0),LocalTime.of(10,0),
                "x".repeat(81),"LECTURE",null,null,null)));
        invalid(() -> validate(new TimetableInput(1,"MONDAY",LocalTime.of(9,0),LocalTime.of(10,0),
                null,"LECTURE",0,null,null)));
    }

    public void testNavigationRejectsWrongRolesAndLoggedOutUsersBeforeLoadingFxml() throws Exception {
        var routes = java.util.Map.of("ADMIN", "/fxml/admin_timetable.fxml", "TECHNICAL_OFFICER",
                "/fxml/timetable.fxml", "STUDENT", "/fxml/student_timetable.fxml");
        for (var route : routes.entrySet()) {
            SessionManager.logout();
            denied(() -> NavigationUtil.navigate(null, route.getValue(), "Denied"));
            for (String role : List.of("ADMIN", "TECHNICAL_OFFICER", "STUDENT", "LECTURER")) {
                login(role);
                if (!role.equals(route.getKey())) denied(() -> NavigationUtil.navigate(null, route.getValue(), "Denied"));
            }
        }
    }

    public void testServiceRolesAndStaleSession() throws Exception {
        for (String role : List.of("ADMIN", "TECHNICAL_OFFICER", "STUDENT", "LECTURER")) {
            login(role);
            if (!role.equals("ADMIN")) denied(AdminTimetableService::new);
            if (!role.equals("TECHNICAL_OFFICER")) denied(TimetableService::new);
            if (!role.equals("STUDENT")) denied(StudentTimetableService::new);
        }
        login("ADMIN"); var admin = new AdminTimetableService(); login("ADMIN"); denied(admin::getAllTimetable);
        login("TECHNICAL_OFFICER"); var officer = new TimetableService(); SessionManager.logout(); denied(officer::getDepartmentTimetable);
        login("STUDENT"); var student = new StudentTimetableService(); login("STUDENT"); denied(student::getMyTimetable);
        try { SessionManager.startSession(new User(1,"test","Test",null,"STUDENT",false)); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    public void testFxmlControllerFieldsAndHandlers() throws Exception {
        for (String screen : List.of("admin_timetable", "timetable", "student_timetable")) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(getClass().getResourceAsStream("/fxml/"+screen+".fxml"));
            var type = Class.forName(doc.getDocumentElement().getAttribute("fx:controller"));
            var ids = new java.util.HashSet<String>();
            var methods = new java.util.HashSet<String>();
            for (var method : type.getDeclaredMethods()) methods.add(method.getName());
            var elements = doc.getElementsByTagName("*");
            for (int i=0; i<elements.getLength(); i++) {
                var element = (org.w3c.dom.Element) elements.item(i);
                String id = element.getAttribute("fx:id"), action = element.getAttribute("onAction");
                if (!id.isEmpty()) assertTrue(ids.add(id));
                if (action.startsWith("#")) assertTrue(action, methods.contains(action.substring(1)));
                if (!screen.equals("admin_timetable")) assertFalse(List.of("#handleAdd", "#handleUpdate", "#handleDelete").contains(action));
            }
            for (var field : type.getDeclaredFields()) if (field.isAnnotationPresent(javafx.fxml.FXML.class))
                assertTrue(screen+" missing "+field.getName(), ids.contains(field.getName()));
        }
    }

    public void testStudentCannotUseRetainedAdminWriteMethods() throws Exception {
        login("ADMIN");
        var admin = new AdminTimetableService(new TimetableDAO(), () -> {
            throw new AssertionError("Unauthorized timetable writes must be rejected before opening a connection");
        });
        login("STUDENT");
        var studentIdentity = SessionManager.requireSession();
        var details = input(1, "MONDAY", "LECTURE", "09:00", "10:00", null, null);
        denied(() -> admin.createTimetableEntry(details));
        denied(() -> admin.updateTimetableEntry(1, details));
        denied(() -> admin.deleteTimetableEntry(1));
        assertTrue("Denied writes must preserve the student session", SessionManager.isCurrentSession(studentIdentity));
    }

    public void testRootDatabaseErrorIsVisible() {
        assertTrue(TimetableTaskRunner.errorMessage("Load failed.", new IllegalStateException("wrapper",
                new java.sql.SQLException("Unknown column example"))).contains("Unknown column example"));
        String connectionError = TimetableTaskRunner.errorMessage("Could not load timetable.",
                new java.sql.SQLException("Connection failed", "08001", new java.net.ConnectException("Connection refused")));
        assertTrue(connectionError.contains("click Refresh before retrying a change"));
        assertTrue(connectionError.contains("Details: Connection refused"));
        assertTrue(TimetableTaskRunner.errorMessage("Could not load timetable.",
                new java.sql.SQLException("Access denied", "28000")).contains("Ask your administrator"));
    }

    static TimetableInput input(int id, String day, String type, String start, String end, LocalDate from, LocalDate to) {
        return new TimetableInput(id, day, LocalTime.parse(start), LocalTime.parse(end), null, type, null, from, to);
    }
    private static TimetableInput validate(TimetableInput input) throws Exception {
        var method = TimetableDAO.class.getDeclaredMethod("validate", TimetableInput.class); method.setAccessible(true);
        return (TimetableInput) invoke(method, input);
    }
    private static LocalTime parse(String input) throws Exception {
        var method = AdminTimetableController.class.getDeclaredMethod("parseTime", String.class, String.class);
        method.setAccessible(true); return (LocalTime) invoke(method, input, "Start time");
    }
    private static Object invoke(java.lang.reflect.Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }
    private static void login(String role) { SessionManager.startSession(new User(1,"test","Test",null,role,true)); }
    interface Action { void run() throws Exception; }
    private static void invalid(Action action) throws Exception { try { action.run(); fail("Expected invalid input"); } catch (IllegalArgumentException expected) { } }
    private static void denied(Action action) throws Exception { try { action.run(); fail("Expected denied access"); } catch (SecurityException expected) { } }
}
