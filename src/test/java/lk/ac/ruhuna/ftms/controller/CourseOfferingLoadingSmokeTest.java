package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseOffering;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Read-only regression against existing ftms_group14 data and actual dashboard navigation.
 * Run explicitly: mvn -Dtest=CourseOfferingLoadingSmokeTest test
 * No fixture inserts, updates, deletes or schema changes; no visible window is opened.
 */
public class CourseOfferingLoadingSmokeTest extends TestCase {
    public void testExistingOfferingsLoadThroughDashboardAndRefresh() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement(); var row = statement.executeQuery("SELECT DATABASE()")) {
                assertTrue(row.next());
                assertEquals("ftms_group14", row.getString(1));
            }
            var before = snapshot(connection);
            int offeringCount = before.get("course_offerings").size();
            assertTrue("This regression needs existing offerings", offeringCount > 0);
            var dao = new CourseOfferingDAO();
            var offerings = dao.getAllOfferings();
            assertEquals("The SELECT joins must retain every existing offering", offeringCount, offerings.size());
            var courses = dao.getActiveCourses();
            var semesters = dao.getSemesters();
            assertFalse("Existing active courses are required", courses.isEmpty());
            assertFalse("Existing semesters are required", semesters.isEmpty());
            assertEquals(before.get("semesters").size(), semesters.size());
            var admin = new UserDAO().getAllUsers().stream()
                    .filter(user -> user.isActive() && "ADMIN".equals(user.getRole())).findFirst().orElseThrow();

            System.out.printf("Database=ftms_group14; courses=%d; offerings=%d; active courses=%d; semesters=%d%n",
                    before.get("courses").size(), offeringCount, courses.size(), semesters.size());
            var started = new CountDownLatch(1);
            Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
            assertTrue("JavaFX startup", started.await(10, TimeUnit.SECONDS));
            try {
                var work = new FutureTask<Void>(() -> {
                    SessionManager.startSession(admin);
                    try {
                        Scene scene = new Scene(FXMLLoader.load(getClass().getResource("/fxml/admin_dashboard.fxml")));
                        for (boolean card : new boolean[]{false, true}) {
                            layout(scene);
                            scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance)
                                    .map(Button.class::cast)
                                    .filter(button -> button.getText().contains("Course Offerings")
                                            && button.getText().contains("Manage") == card)
                                    .findFirst().orElseThrow().fire();
                            layout(scene);
                            assertEquals("courseOfferingsRoot", scene.getRoot().getId());
                            TableView<CourseOffering> table = table(scene, "offeringTable");
                            assertEquals("Opening through " + (card ? "card" : "sidebar")
                                    + " must inject the service and load existing offerings", offeringCount, table.getItems().size());
                            assertEquals(courses, combo(scene, "courseCombo").getItems());
                            assertEquals(semesters, combo(scene, "semesterCombo").getItems());
                            assertFalse("Assignment service loads eligible lecturers",
                                    combo(scene, "lecturerComboBox").getItems().isEmpty());
                            assertRowsAndCells(table, offerings);
                            assertAssignments(scene, connection);

                            TextField search = (TextField) scene.lookup("#searchField");
                            search.setText("missing-" + UUID.randomUUID());
                            button(scene, "Search").fire();
                            assertTrue("Search can return no matches", table.getItems().isEmpty());
                            button(scene, "Refresh").fire();
                            assertEquals("Refresh loads every existing offering", offeringCount, table.getItems().size());
                            assertEquals("", search.getText());
                            assertEquals(courses, combo(scene, "courseCombo").getItems());
                            assertEquals(semesters, combo(scene, "semesterCombo").getItems());
                            assertRowsAndCells(table, offerings);
                            assertTrue(scene.lookup("#assignmentEditor").isDisabled());
                            assertTrue(table(scene, "assignedLecturerTable").getItems().isEmpty());
                            button(scene, "Back to Admin Dashboard").fire();
                            assertEquals("adminRoot", scene.getRoot().getId());
                        }
                        return null;
                    } finally {
                        SessionManager.logout();
                    }
                });
                Platform.runLater(work);
                work.get(45, TimeUnit.SECONDS);
            } finally {
                Platform.exit();
            }
            assertEquals("Screen reads must leave existing database records unchanged", before, snapshot(connection));
            System.out.println("PASS: dashboard sidebar/card, all seven columns, dropdowns, assignments, search/Refresh; database unchanged.");
        }
    }

    private static void assertRowsAndCells(TableView<CourseOffering> table, List<CourseOffering> expected) {
        assertEquals(expected.stream().map(CourseOffering::getOfferingId).toList(),
                table.getItems().stream().map(CourseOffering::getOfferingId).toList());
        assertEquals(7, table.getColumns().size());
        for (int row = 0; row < expected.size(); row++) {
            CourseOffering offering = expected.get(row);
            var values = List.of(offering.getCourseCode(), offering.getCourseName(), offering.getCredits(),
                    offering.getSemesterName(), offering.getAcademicYearName(), offering.getOfferingGroup(), offering.getStatus());
            for (int column = 0; column < values.size(); column++) {
                assertEquals("Offering " + offering.getOfferingId() + ", column " + column,
                        values.get(column), table.getColumns().get(column).getCellData(row));
            }
        }
    }

    private static void assertAssignments(Scene scene, Connection connection) throws Exception {
        TableView<CourseOffering> offerings = table(scene, "offeringTable");
        TableView<LecturerCourseAssignment> assigned = table(scene, "assignedLecturerTable");
        assertTrue("No selection disables assignment editing", scene.lookup("#assignmentEditor").isDisabled());
        assertTrue(button(scene, "Refresh Assignments").isDisabled());
        int assignmentCount = 0;
        for (CourseOffering offering : offerings.getItems()) {
            offerings.getSelectionModel().select(offering);
            assertEquals("CANCELLED".equals(offering.getStatus()), scene.lookup("#assignmentEditor").isDisabled());
            assertFalse(button(scene, "Refresh Assignments").isDisabled());
            assertEquals(offering.getCourseId(), ((CourseOfferingDAO.CourseOption) combo(scene, "courseCombo").getValue()).courseId());
            assertEquals(offering.getSemesterId(), ((CourseOfferingDAO.SemesterOption) combo(scene, "semesterCombo").getValue()).semesterId());
            assertTrue(button(scene, "Update Assignment Type").isDisabled());
            assertTrue(button(scene, "Remove Assignment").isDisabled());
            var expectedIds = new ArrayList<Integer>();
            try (var statement = connection.prepareStatement(
                    "SELECT assignment_id FROM lecturer_course_assignments WHERE offering_id=? ORDER BY assignment_id")) {
                statement.setInt(1, offering.getOfferingId());
                try (var rows = statement.executeQuery()) { while (rows.next()) expectedIds.add(rows.getInt(1)); }
            }
            assertEquals("Selecting offering loads its assigned lecturers", expectedIds,
                    assigned.getItems().stream().map(LecturerCourseAssignment::getAssignmentId).sorted().toList());
            assignmentCount += assigned.getItems().size();
            for (var assignment : assigned.getItems()) {
                assertEquals(offering.getOfferingId(), assignment.getOfferingId());
                assertEquals(offering.getCourseCode(), assignment.getCourseCode());
            }
            if (!assigned.getItems().isEmpty()) {
                assigned.getSelectionModel().selectFirst();
                assertEquals("CANCELLED".equals(offering.getStatus()), button(scene, "Update Assignment Type").isDisabled());
                assertEquals("CANCELLED".equals(offering.getStatus()), button(scene, "Remove Assignment").isDisabled());
                assertEquals(assigned.getItems().getFirst().getAssignmentType(), combo(scene, "assignmentTypeComboBox").getValue());
                button(scene, "Refresh Assignments").fire();
                assertEquals(expectedIds.size(), assigned.getItems().size());
            }
            String message = ((Label) scene.lookup("#assignmentStatusLabel")).getText();
            assertTrue("No assignment loading error: " + message, message.isEmpty()
                    || ("CANCELLED".equals(offering.getStatus()) && message.startsWith("Cancelled offering:")));
        }
        assertTrue("Verify at least one existing lecturer assignment", assignmentCount > 0);
        System.out.println("Verified existing lecturer assignments: " + assignmentCount);
        button(scene, "Clear Form").fire();
        assertTrue(assigned.getItems().isEmpty());
        assertTrue(scene.lookup("#assignmentEditor").isDisabled());
        assertTrue(button(scene, "Refresh Assignments").isDisabled());
    }

    private static Map<String, List<List<String>>> snapshot(Connection connection) throws Exception {
        var snapshot = new LinkedHashMap<String, List<List<String>>>();
        for (String table : List.of("courses", "course_offerings", "semesters", "academic_years", "lecturer_course_assignments")) {
            var data = new ArrayList<List<String>>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                while (rows.next()) {
                    var row = new ArrayList<String>();
                    for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) row.add(rows.getString(column));
                    data.add(row);
                }
            }
            snapshot.put(table, data);
        }
        return snapshot;
    }

    private static void layout(Scene scene) { scene.getRoot().applyCss(); scene.getRoot().layout(); }
    private static ComboBox<?> combo(Scene scene, String id) { return (ComboBox<?>) scene.lookup("#" + id); }
    @SuppressWarnings("unchecked")
    private static <T> TableView<T> table(Scene scene, String id) { return (TableView<T>) scene.lookup("#" + id); }
    private static Button button(Scene scene, String text) {
        return scene.getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }
}
