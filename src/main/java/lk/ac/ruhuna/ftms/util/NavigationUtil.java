
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

    // ==========================================
    // NOTICES ACCESS
    // ==========================================

    private static void requireNoticeAccess(String resource) {

        var user = SessionManager.getCurrentUser();

        // Only Admin can manage notices
        if ("/fxml/notices.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active administrator access is required to manage notices."
            );
        }

        // Authorized users can view notices
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

    // ==========================================
    // ROLE-BASED DASHBOARD ROUTING
    // ==========================================

    public static String dashboardResource() {

        var user = SessionManager.getCurrentUser();

        if (user == null || !user.isActive()) {

            throw new SecurityException(
                    "Please sign in."
            );
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

    // ==========================================
    // MAIN NAVIGATION
    // ==========================================

    public static void navigate(
            Node source,
            String resource,
            String title) throws IOException {

        // Redirect generic dashboard route
        if ("/fxml/dashboard.fxml".equals(resource)) {

            resource = dashboardResource();
        }

        // Check notice permissions
        requireNoticeAccess(resource);

        var user = SessionManager.getCurrentUser();

        // ======================================
        // ADMIN COURSE MANAGEMENT
        // ======================================

        if ("/fxml/courses.fxml".equals(resource)
                && !CourseService.isCurrentUserAdmin()) {

            throw new SecurityException(
                    "Active administrator access is required to manage courses."
            );
        }

        // ======================================
        // ADMIN TIMETABLE MANAGEMENT
        // ======================================

        if ("/fxml/admin_timetable.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active administrator access is required to manage timetables."
            );
        }

        // ======================================
        // TECHNICAL OFFICER ATTENDANCE
        // ======================================

        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required for attendance management."
            );
        }

        // ======================================
        // TECHNICAL OFFICER TIMETABLE
        // VIEW ONLY
        // ======================================

        if ("/fxml/timetable.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active Technical Officer session is required for the timetable."
            );
        }

        // ======================================
        // STUDENT-ONLY SCREENS
        // ======================================

        Set<String> studentViews = Set.of(
                "/fxml/student_dashboard.fxml",
                "/fxml/student_attendance.fxml",
                "/fxml/student_eligibility.fxml",
                "/fxml/student_medical.fxml",
                "/fxml/student_courses.fxml",
                "/fxml/student_results.fxml",
                "/fxml/student_timetable.fxml"
        );

        // Only active Students can open Student pages
        if (studentViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        // Restrict students to Student Portal
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

        // ======================================
        // LECTURER ACCESS
        // ======================================

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

        // ======================================
        // TECHNICAL OFFICER ACCESS
        // ======================================

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

        // ======================================
        // LOAD FXML
        // ======================================

        URL location =
                NavigationUtil.class.getResource(resource);

        if (location == null) {

            throw new IOException(
                    "Screen not found: " + resource
            );
        }

        FXMLLoader loader = new FXMLLoader(location);

        Parent root = loader.load();

        // ======================================
        // PRESERVE COURSE SERVICE INJECTION
        // ======================================

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

        // ======================================
        // PRESERVE RESPONSIVE WINDOW SIZE
        // ======================================

        Scene scene = source.getScene();

        boolean preserveWindowSize =

                // Student Portal
                studentViews.contains(resource)

                        // Admin
                        || "/fxml/admin_dashboard.fxml".equals(resource)
                        || "/fxml/admin_timetable.fxml".equals(resource)
                        || "/fxml/courses.fxml".equals(resource)
                        || "coursesRoot".equals(scene.getRoot().getId())
                        || "adminTimetableRoot".equals(scene.getRoot().getId())

                        // Technical Officer
                        || "/fxml/technical_officer_dashboard.fxml".equals(resource)
                        || "/fxml/attendance.fxml".equals(resource)
                        || "/fxml/timetable.fxml".equals(resource)

                        // Login
                        || "/fxml/login.fxml".equals(resource)

                        // Notices
                        || "/fxml/notices.fxml".equals(resource)
                        || "/fxml/view_notices.fxml".equals(resource)
                        || "noticesRoot".equals(scene.getRoot().getId())
                        || "viewNoticesRoot".equals(scene.getRoot().getId())

                        // Student roots
                        || "studentRoot".equals(scene.getRoot().getId())
                        || "studentEligibilityRoot".equals(scene.getRoot().getId())
                        || "studentAttendanceRoot".equals(scene.getRoot().getId())

                        // Technical Officer roots
                        || "technicalOfficerRoot".equals(scene.getRoot().getId())
                        || "attendanceRoot".equals(scene.getRoot().getId())
                        || "timetableRoot".equals(scene.getRoot().getId());

        // Replace current screen
        scene.setRoot(root);

        // Update window title
        if (scene.getWindow() instanceof Stage stage) {

            stage.setTitle(title);

            if (!preserveWindowSize) {

                stage.sizeToScene();
            }
        }
    }
}
