
package lk.ac.ruhuna.ftms.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import lk.ac.ruhuna.ftms.controller.CourseController;
import lk.ac.ruhuna.ftms.controller.CourseOfferingController;
import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.service.CourseService;
import lk.ac.ruhuna.ftms.service.CourseOfferingService;
import lk.ac.ruhuna.ftms.service.LecturerAssignmentService;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

public final class NavigationUtil {

    private NavigationUtil() {
    }

    private static void requireNoticeAccess(String resource) {

        var user = SessionManager.getCurrentUser();

        if ("/fxml/notices.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active administrator access is required to manage notices."
            );
        }

        if ("/fxml/view_notices.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || user.getRole() == null
                || !Set.of(
                "ADMIN", "LECTURER",
                "STUDENT", "TECHNICAL_OFFICER"
        ).contains(user.getRole()))) {

            throw new SecurityException(
                    "Please sign in with an active FTMS account to view notices."
            );
        }
    }

    public static String dashboardResource() {

        var user = SessionManager.getCurrentUser();

        if (user == null || !user.isActive()) {
            throw new SecurityException("Please sign in.");
        }

        return switch (user.getRole()) {
            case "LECTURER" ->
                    "/fxml/lecturer_dashboard.fxml";
            case "ADMIN" ->
                    "/fxml/admin_dashboard.fxml";
            case "STUDENT" ->
                    "/fxml/student_dashboard.fxml";
            case "TECHNICAL_OFFICER" ->
                    "/fxml/technical_officer_dashboard.fxml";
            default ->
                    "/fxml/dashboard.fxml";
        };
    }

    public static void navigate(
            Node source,
            String resource,
            String title) throws IOException {

        if ("/fxml/dashboard.fxml".equals(resource)) {
            resource = dashboardResource();
        }

        requireNoticeAccess(resource);

        // Preserve Admin Course Management
        if ("/fxml/courses.fxml".equals(resource)
                && !CourseService.isCurrentUserAdmin()) {

            throw new SecurityException(
                    "Active administrator access is required to manage courses."
            );
        }
        var user = SessionManager.getCurrentUser();

        String timetableRole = switch (resource) {
            case "/fxml/admin_timetable.fxml" -> "ADMIN";
            case "/fxml/timetable.fxml" -> "TECHNICAL_OFFICER";
            case "/fxml/student_timetable.fxml" -> "STUDENT";
            default -> null;
        };
        if (timetableRole != null && (user == null || !user.isActive()
                || !timetableRole.equals(user.getRole()))) {
            throw new SecurityException("Active " + timetableRole + " access is required for this timetable.");
        }

        // Technical Officer attendance management
        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required for attendance management."
            );
        }

        // Student-only screens
        Set<String> studentViews = Set.of(
                "/fxml/student_dashboard.fxml",
                "/fxml/student_attendance.fxml",
                "/fxml/student_eligibility.fxml",
                "/fxml/student_medical.fxml",
                "/fxml/student_courses.fxml",
                "/fxml/student_results.fxml",
                "/fxml/student_timetable.fxml"
        );

        // Protect Student Portal
        if (studentViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        if (user != null
                && "STUDENT".equals(user.getRole())
                && (!user.isActive()
                || (!studentViews.contains(resource)
                && !Set.of(
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/assessments.fxml", "/fxml/results.fxml", "/fxml/eligibility.fxml",
                    "/fxml/reports.fxml", "/fxml/gpa.fxml",
                    "/fxml/login.fxml"
        ).contains(resource)))) {

            throw new SecurityException(
                    "Use your student workspace to access your own records."
            );
        }

        // Preserve Lecturer access
        if (user != null
                && "LECTURER".equals(user.getRole())
                && !Set.of(
                "/fxml/lecturer_dashboard.fxml",
                "/fxml/lecturer_workspace.fxml",
                "/fxml/marks_entry.fxml",
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/login.fxml"
        ).contains(resource)) {

            throw new SecurityException(
                    "Use the lecturer workspace for authorized course records."
            );
        }
        URL location = NavigationUtil.class.getResource(resource);
        if (location == null) {
            throw new IOException(
                    "Screen not found: " + resource
            );
        }

        FXMLLoader loader = new FXMLLoader(location);
        Parent root = loader.load();

        // Preserve CourseService dependency injection
        if ("/fxml/courses.fxml".equals(resource)) {

            CourseController controller =
                    loader.getController();

            controller.setCourseService(
                    new CourseService(
                            new CourseDAO(),
                            CourseService::isCurrentUserAdmin
                    )
            );
        }
        Scene scene = source.getScene();

        // Preserve responsive window dimensions
        boolean preserveWindowSize =
                timetableRole != null
                        || studentViews.contains(resource)
                        || "/fxml/admin_dashboard.fxml".equals(resource)
                        || "/fxml/courses.fxml".equals(resource)
                        || "/fxml/course_offerings.fxml".equals(resource)
                || Set.of("adminTimetableRoot", "timetableRoot", "studentTimetableRoot")
                        .contains(String.valueOf(scene.getRoot().getId()))
                || "courseOfferingsRoot".equals(scene.getRoot().getId())
                || "coursesRoot".equals(scene.getRoot().getId())
                        || "/fxml/technical_officer_dashboard.fxml".equals(resource)
                        || "/fxml/attendance.fxml".equals(resource)
                        || "/fxml/login.fxml".equals(resource)
                        || "/fxml/notices.fxml".equals(resource)
                        || "/fxml/view_notices.fxml".equals(resource)
                        || "noticesRoot".equals(scene.getRoot().getId())
                        || "viewNoticesRoot".equals(scene.getRoot().getId())
                        || "studentRoot".equals(scene.getRoot().getId())
                        || "studentEligibilityRoot".equals(scene.getRoot().getId())
                        || "studentAttendanceRoot".equals(scene.getRoot().getId())
                        || "technicalOfficerRoot".equals(scene.getRoot().getId())
                        || "attendanceRoot".equals(scene.getRoot().getId());
        scene.setRoot(root);

        if (scene.getWindow() instanceof Stage stage) {

            stage.setTitle(title);

            if (!preserveWindowSize) {
                stage.sizeToScene();
            }
            if (timetableRole != null) fitTimetableWindow(stage);
        }
    }

    /** Clamp outer window bounds, including native title-bar controls, to the current display. */
    private static void fitTimetableWindow(Stage stage) {
        if (stage.isMaximized() || stage.isFullScreen()) return;
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight());
        Rectangle2D bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        stage.setWidth(Math.min(stage.getWidth(), bounds.getWidth()));
        stage.setHeight(Math.min(stage.getHeight(), bounds.getHeight()));
        stage.setX(Math.max(bounds.getMinX(), Math.min(stage.getX(), bounds.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(bounds.getMinY(), Math.min(stage.getY(), bounds.getMaxY() - stage.getHeight())));
    }
}
