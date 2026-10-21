
package lk.ac.ruhuna.ftms.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import lk.ac.ruhuna.ftms.controller.CourseController;
import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.service.CourseService;

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
                "ADMIN",
                "LECTURER",
                "STUDENT",
                "TECHNICAL_OFFICER"
        ).contains(user.getRole()))) {

            throw new SecurityException(
                    "Please sign in with an active FTMS account to view notices."
            );
        }
    }

    // Route users to their original dashboards

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

    // Open an authorized JavaFX screen

    public static void navigate(
            Node source,
            String resource,
            String title) throws IOException {

        if ("/fxml/dashboard.fxml".equals(resource)) {
            resource = dashboardResource();
        }

        requireNoticeAccess(resource);

        // Preserve administrator course management

        if ("/fxml/courses.fxml".equals(resource)
                && !CourseService.isCurrentUserAdmin()) {

            throw new SecurityException(
                    "Active administrator access is required to manage courses."
            );
        }

        var user = SessionManager.getCurrentUser();

        // Technical Officer attendance access

        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required for attendance management."
            );
        }

        // NEW: Technical Officer timetable access

        if ("/fxml/timetable.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active Technical Officer session is required for the timetable."
            );
        }

        // Preserve Student-only pages

        Set<String> studentViews = Set.of(
                "/fxml/student_dashboard.fxml",
                "/fxml/student_attendance.fxml",
                "/fxml/student_eligibility.fxml",
                "/fxml/student_medical.fxml",
                "/fxml/student_courses.fxml",
                "/fxml/student_results.fxml"
        );

        if (studentViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        // Students may access only student screens

        if (user != null
                && "STUDENT".equals(user.getRole())
                && (!user.isActive()
                || (!studentViews.contains(resource)
                && !Set.of(
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
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

        // Preserve Technical Officer access restrictions

        if (user != null
                && "TECHNICAL_OFFICER".equals(user.getRole())
                && !Set.of(
                "/fxml/technical_officer_dashboard.fxml",
                "/fxml/attendance.fxml",
                "/fxml/timetable.fxml",
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/login.fxml"
        ).contains(resource)) {

            throw new SecurityException(
                    "Use the Technical Officer workspace for authorized department features."
            );
        }

        // Load FXML

        URL location =
                NavigationUtil.class.getResource(resource);

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

        // Keep responsive window size

        boolean preserveWindowSize =
                studentViews.contains(resource)

                        || "/fxml/admin_dashboard.fxml".equals(resource)
                        || "/fxml/courses.fxml".equals(resource)
                        || "coursesRoot".equals(scene.getRoot().getId())

                        || "/fxml/technical_officer_dashboard.fxml".equals(resource)
                        || "/fxml/attendance.fxml".equals(resource)
                        || "/fxml/timetable.fxml".equals(resource)
                        || "/fxml/login.fxml".equals(resource)

                        || "/fxml/notices.fxml".equals(resource)
                        || "/fxml/view_notices.fxml".equals(resource)

                        || "noticesRoot".equals(scene.getRoot().getId())
                        || "viewNoticesRoot".equals(scene.getRoot().getId())

                        || "studentRoot".equals(scene.getRoot().getId())
                        || "studentEligibilityRoot".equals(scene.getRoot().getId())
                        || "studentAttendanceRoot".equals(scene.getRoot().getId())

                        || "technicalOfficerRoot".equals(scene.getRoot().getId())
                        || "attendanceRoot".equals(scene.getRoot().getId())
                        || "timetableRoot".equals(scene.getRoot().getId());

        scene.setRoot(root);

        if (scene.getWindow() instanceof Stage stage) {

            stage.setTitle(title);

            if (!preserveWindowSize) {
                stage.sizeToScene();
            }
        }
    }
}
