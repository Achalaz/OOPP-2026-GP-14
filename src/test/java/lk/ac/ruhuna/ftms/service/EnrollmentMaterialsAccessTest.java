package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline role/session and filesystem regressions. MySQL integration runs separately. */
public class EnrollmentMaterialsAccessTest extends TestCase {
    protected void tearDown() { SessionManager.logout(); }

    public void testRoleAndNavigationGuards() throws Exception {
        SessionManager.logout();
        denied(AdminEnrollmentService::new);
        denied(StudentCourseMaterialService::new);
        for (String role : List.of("ADMIN", "STUDENT", "LECTURER", "TECHNICAL_OFFICER")) {
            login(role);
            if (!"ADMIN".equals(role)) {
                denied(AdminEnrollmentService::new);
                denied(() -> NavigationUtil.navigate(null, "/fxml/admin_enrollments.fxml", "Denied"));
            }
            if (!"STUDENT".equals(role)) {
                denied(StudentCourseMaterialService::new);
                denied(() -> NavigationUtil.navigate(null, "/fxml/student_materials.fxml", "Denied"));
                denied(() -> NavigationUtil.navigate(null, "/fxml/student_courses.fxml", "Denied"));
            }
            if (!"LECTURER".equals(role)) {
                denied(() -> NavigationUtil.navigate(null, "/fxml/course_materials.fxml", "Denied"));
                denied(() -> NavigationUtil.navigate(null, "/fxml/lecturer_workspace.fxml", "Denied"));
            }
        }
    }

    public void testOldServicesCannotAdoptNewLogin() throws Exception {
        login("ADMIN");
        var admin = new AdminEnrollmentService();
        login("ADMIN");
        denied(admin::findAllEnrollments);
        denied(() -> admin.enrollStudent(3, 10, 1));
        denied(() -> admin.updateEnrollmentStatus(1, "ENROLLED", "WITHDRAWN"));
        login("STUDENT");
        var student = new StudentCourseMaterialService();
        login("STUDENT");
        denied(() -> student.list(10));
        denied(() -> student.download(10, 1));
        denied(() -> student.export(10, 1, Path.of("target", "forbidden-export.pdf")));
    }

    public void testStudentHasNoMaterialManagementMethods() {
        for (var method : StudentCourseMaterialService.class.getMethods())
            assertFalse(List.of("save", "upload", "update", "delete").contains(method.getName()));
    }

    public void testSharedStorageRejectsTraversalAndKeepsDestinationOnCancelledExport() throws Exception {
        Path temp = Files.createTempDirectory(Path.of("target"), "enrollment-materials-storage-").toAbsolutePath();
        try {
            var storage = new MaterialStorage(temp.resolve("managed"));
            Path source = temp.resolve("source.txt"); Files.writeString(source, "Actual test file bytes");
            String name = storage.store(source);
            assertEquals("Actual test file bytes", new String(storage.read(name), java.nio.charset.StandardCharsets.UTF_8));
            for (String invalid : List.of("../source.txt", source.toString(), "materials/oop/intro.pdf", "https://example.org/a.pdf")) {
                try { storage.read(invalid); fail("Unsafe path accepted"); } catch (java.io.IOException expected) { }
            }
            Path destination = temp.resolve("saved.txt"); Files.writeString(destination, "Keep existing document");
            int[] checks = {0};
            try {
                storage.export(storage.read(name), destination, () -> {
                    if (++checks[0] == 2) throw new SecurityException("Session ended during export");
                });
                fail("Expected revalidation failure");
            } catch (SecurityException expected) { }
            assertEquals("Keep existing document", Files.readString(destination));
            try (var files = Files.list(temp)) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".ftms-export-"))); }
            Thread.currentThread().interrupt();
            try { storage.read(name); fail("Cancelled read accepted"); }
            catch (java.util.concurrent.CancellationException expected) { }
            finally { Thread.interrupted(); }
        } finally {
            try (var files = Files.walk(temp)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    public void testNewFxmlFieldsAndReadOnlyActions() throws Exception {
        for (String name : List.of("admin_enrollments", "student_materials")) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(getClass().getResourceAsStream("/fxml/" + name + ".fxml"));
            var controller = Class.forName(document.getDocumentElement().getAttribute("fx:controller"));
            var ids = new java.util.HashSet<String>();
            var methods = new java.util.HashSet<String>();
            for (var method : controller.getDeclaredMethods()) methods.add(method.getName());
            var nodes = document.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                var element = (org.w3c.dom.Element) nodes.item(i);
                String id = element.getAttribute("fx:id"), action = element.getAttribute("onAction");
                if (!id.isEmpty()) assertTrue(ids.add(id));
                if (action.startsWith("#")) assertTrue(action, methods.contains(action.substring(1)));
                if (name.equals("student_materials"))
                    assertFalse(action.matches("(?i).*(upload|delete|edit|enroll).*$"));
            }
            for (var field : controller.getDeclaredFields())
                if (field.isAnnotationPresent(javafx.fxml.FXML.class)) assertTrue(field.getName(), ids.contains(field.getName()));
        }
    }

    private void login(String role) { SessionManager.startSession(new User(3, "fixture", "Fixture", null, role, true)); }
    interface Action { void run() throws Exception; }
    static void denied(Action action) throws Exception {
        try { action.run(); fail("Expected authorization rejection"); } catch (SecurityException expected) { }
    }
}
