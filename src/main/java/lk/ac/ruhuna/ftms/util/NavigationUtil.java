package lk.ac.ruhuna.ftms.util;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import lk.ac.ruhuna.ftms.controller.CourseController;
import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.service.CourseService;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Centralized navigation for FTMS.
 *
 * Preserves existing role restrictions
 * and responsive window behavior.
 */
public final class NavigationUtil {

    private NavigationUtil() {
    }

    // ==========================================
    // NOTICE ACCESS CONTROL
    // ==========================================

    private static void requireNoticeAccess(
            String resource) {

        var user = SessionManager.getCurrentUser();

        // Only Admin can manage notices
        if ("/fxml/notices.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"ADMIN".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active administrator access is required "
                            + "to manage notices."
            );
        }

        // Active FTMS users can view notices
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
                    "Please sign in with an active FTMS "
                            + "account to view notices."
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
    // NORMAL NAVIGATION
    // ==========================================

    public static void navigate(
            Node source,
            String resource,
            String title) throws IOException {

        navigate(
                source,
                resource,
                title,
                controller -> {
                }
        );
    }

    // ==========================================
    // NAVIGATION WITH CONTROLLER CONFIGURATION
    // ==========================================

    public static void navigate(
            Node source,
            String resource,
            String title,
            Consumer<Object> configure
    ) throws IOException {

        // Resolve generic dashboard
        if ("/fxml/dashboard.fxml".equals(resource)) {

            resource = dashboardResource();
        }

        // Verify notice permissions
        requireNoticeAccess(resource);

        var user = SessionManager.getCurrentUser();

        // ======================================
        // ADMIN COURSE MANAGEMENT
        // ======================================

        if ("/fxml/courses.fxml".equals(resource)
                && !CourseService.isCurrentUserAdmin()) {

            throw new SecurityException(
                    "Active administrator access is required "
                            + "to manage courses."
            );
        }

        // ======================================
        // STUDENT RESULTS REDIRECT
        // ======================================

        if ("/fxml/results.fxml".equals(resource)
                && user != null
                && "STUDENT".equals(user.getRole())) {

            resource = "/fxml/student_results.fxml";
        }

        // ======================================
        // LECTURER ACADEMIC SCREENS
        // ======================================

        Set<String> lecturerAcademicViews = Set.of(
                "/fxml/assessments.fxml",
                "/fxml/results.fxml",
                "/fxml/eligibility.fxml",
                "/fxml/marks_entry.fxml"
        );

        if (lecturerAcademicViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"LECTURER".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active assigned lecturer is required "
                            + "for academic management."
            );
        }

        // GPA and Reports
        if (Set.of(
                "/fxml/gpa.fxml",
                "/fxml/reports.fxml"
        ).contains(resource)
                && (user == null
                || !user.isActive()
                || !Set.of(
                "LECTURER",
                "STUDENT"
        ).contains(user.getRole()))) {

            throw new SecurityException(
                    "An active lecturer or student account "
                            + "is required for this academic view."
            );
        }

        // ======================================
        // ADMIN / LECTURER ROLE ROUTES
        // ======================================

        String requiredRole = switch (resource) {

            case "/fxml/admin_enrollments.fxml",
                 "/fxml/admin_dashboard.fxml" ->
                    "ADMIN";

            case "/fxml/lecturer_workspace.fxml",
                 "/fxml/course_materials.fxml" ->
                    "LECTURER";

            default -> null;
        };

        if (requiredRole != null
                && (user == null
                || !user.isActive()
                || !requiredRole.equals(user.getRole()))) {

            throw new SecurityException(
                    "Active " + requiredRole
                            + " access is required for this screen."
            );
        }

        // ======================================
        // TIMETABLE ROLE AUTHORIZATION
        // ======================================

        String timetableRole = switch (resource) {

            // Admin can manage timetables
            case "/fxml/admin_timetable.fxml" ->
                    "ADMIN";

            // TO can view department timetable
            case "/fxml/timetable.fxml" ->
                    "TECHNICAL_OFFICER";

            // Student can view own timetable
            case "/fxml/student_timetable.fxml" ->
                    "STUDENT";

            default -> null;
        };

        if (timetableRole != null
                && (user == null
                || !user.isActive()
                || !timetableRole.equals(user.getRole()))) {

            throw new SecurityException(
                    "Active " + timetableRole
                            + " access is required for this timetable."
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
                    "Active Technical Officer access is required "
                            + "for attendance management."
            );
        }

        // ======================================
        // TECHNICAL OFFICER MEDICAL MANAGEMENT
        // ======================================

        if ("/fxml/medical.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required "
                            + "for medical management."
            );
        }

        // ======================================
        // STUDENT-ONLY SCREENS
        // ======================================

        Set<String> studentViews = Set.of(
                "/fxml/student_dashboard.fxml",
                "/fxml/student_attendance.fxml",
                "/fxml/student_eligibility.fxml",

                // Student Medical - View Only
                "/fxml/student_medical.fxml",

                "/fxml/student_courses.fxml",
                "/fxml/student_materials.fxml",
                "/fxml/student_results.fxml",
                "/fxml/student_timetable.fxml"
        );

        // ======================================
        // STUDENT ACCESS AUTHORIZATION
        // ======================================

        if (studentViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        // ======================================
        // STUDENT WORKSPACE RESTRICTIONS
        // ======================================

        if (user != null
                && "STUDENT".equals(user.getRole())
                && (!user.isActive()
                || (!studentViews.contains(resource)
                && !Set.of(
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/reports.fxml",
                "/fxml/gpa.fxml",
                "/fxml/login.fxml"
        ).contains(resource)))) {

            throw new SecurityException(
                    "Use your student workspace "
                            + "to access your own records."
            );
        }

        // ======================================
        // LECTURER WORKSPACE RESTRICTIONS
        // ======================================

        if (user != null
                && "LECTURER".equals(user.getRole())
                && (!user.isActive()
                || !Set.of(
                "/fxml/lecturer_dashboard.fxml",
                "/fxml/lecturer_workspace.fxml",
                "/fxml/course_materials.fxml",
                "/fxml/marks_entry.fxml",
                "/fxml/assessments.fxml",
                "/fxml/results.fxml",
                "/fxml/eligibility.fxml",
                "/fxml/gpa.fxml",
                "/fxml/reports.fxml",
                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/login.fxml"
        ).contains(resource))) {

            throw new SecurityException(
                    "Use the lecturer workspace "
                            + "for authorized course records."
            );
        }

        // ======================================
        // TECHNICAL OFFICER WORKSPACE
        // ======================================

        if (user != null
                && "TECHNICAL_OFFICER".equals(user.getRole())
                && (!user.isActive()
                || !Set.of(
                "/fxml/technical_officer_dashboard.fxml",

                // Attendance Management
                "/fxml/attendance.fxml",

                // Medical Management
                "/fxml/medical.fxml",

                // Department Timetable
                "/fxml/timetable.fxml",

                "/fxml/profile.fxml",
                "/fxml/view_notices.fxml",
                "/fxml/login.fxml"
        ).contains(resource))) {

            throw new SecurityException(
                    "Use the Technical Officer workspace "
                            + "for authorized department features."
            );
        }

        // ======================================
        // LOAD FXML RESOURCE
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
        // OPTIONAL CONTROLLER CONFIGURATION
        // ======================================

        configure.accept(loader.getController());

        // ======================================
        // COURSE SERVICE INJECTION
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
        // PRESERVE WINDOW SIZE
        // ======================================

        Scene scene = source.getScene();

        boolean preserveWindowSize =

                // Timetables
                timetableRole != null

                        // Lecturer screens
                        || lecturerAcademicViews.contains(resource)
                        || Set.of(
                        "/fxml/gpa.fxml",
                        "/fxml/reports.fxml",
                        "/fxml/lecturer_dashboard.fxml"
                ).contains(resource)

                        // Student screens (including Medical)
                        || studentViews.contains(resource)

                        // Admin screens
                        || "/fxml/admin_dashboard.fxml".equals(resource)
                        || "/fxml/admin_enrollments.fxml".equals(resource)
                        || "/fxml/courses.fxml".equals(resource)
                        || "/fxml/course_offerings.fxml".equals(resource)

                        // Existing timetable roots
                        || Set.of(
                        "adminTimetableRoot",
                        "timetableRoot",
                        "studentTimetableRoot"
                ).contains(
                        String.valueOf(
                                scene.getRoot().getId()
                        )
                )

                        // Course roots
                        || "courseOfferingsRoot".equals(
                        scene.getRoot().getId()
                )
                        || "coursesRoot".equals(
                        scene.getRoot().getId()
                )

                        // Technical Officer screens
                        || "/fxml/technical_officer_dashboard.fxml".equals(resource)
                        || "/fxml/attendance.fxml".equals(resource)
                        || "/fxml/medical.fxml".equals(resource)

                        // Login and Notices
                        || "/fxml/login.fxml".equals(resource)
                        || "/fxml/notices.fxml".equals(resource)
                        || "/fxml/view_notices.fxml".equals(resource)

                        // Notices roots
                        || "noticesRoot".equals(
                        scene.getRoot().getId()
                )
                        || "viewNoticesRoot".equals(
                        scene.getRoot().getId()
                )

                        // Student roots
                        || "studentRoot".equals(
                        scene.getRoot().getId()
                )
                        || "studentEligibilityRoot".equals(
                        scene.getRoot().getId()
                )
                        || "studentAttendanceRoot".equals(
                        scene.getRoot().getId()
                )
                        || "studentMedicalRoot".equals(
                        scene.getRoot().getId()
                )

                        // Technical Officer roots
                        || "technicalOfficerRoot".equals(
                        scene.getRoot().getId()
                )
                        || "attendanceRoot".equals(
                        scene.getRoot().getId()
                )
                        || "medicalRoot".equals(
                        scene.getRoot().getId()
                );

        // ======================================
        // DISPLAY NEW SCREEN
        // ======================================

        scene.setRoot(root);

        if (scene.getWindow() instanceof Stage stage) {

            stage.setTitle(title);

            if (!preserveWindowSize) {

                stage.sizeToScene();
            }

            if (timetableRole != null) {

                fitTimetableWindow(stage);
            }
        }
    }

    // ==========================================
    // FIT TIMETABLE WINDOW TO SCREEN
    // ==========================================

    private static void fitTimetableWindow(
            Stage stage) {

        if (stage.isMaximized()
                || stage.isFullScreen()) {

            return;
        }

        var screens =
                Screen.getScreensForRectangle(
                        stage.getX(),
                        stage.getY(),
                        stage.getWidth(),
                        stage.getHeight()
                );

        Rectangle2D bounds =
                (screens.isEmpty()
                        ? Screen.getPrimary()
                        : screens.getFirst())
                        .getVisualBounds();

        stage.setWidth(
                Math.min(
                        stage.getWidth(),
                        bounds.getWidth()
                )
        );

        stage.setHeight(
                Math.min(
                        stage.getHeight(),
                        bounds.getHeight()
                )
        );

        stage.setX(
                Math.max(
                        bounds.getMinX(),
                        Math.min(
                                stage.getX(),
                                bounds.getMaxX()
                                        - stage.getWidth()
                        )
                )
        );

        stage.setY(
                Math.max(
                        bounds.getMinY(),
                        Math.min(
                                stage.getY(),
                                bounds.getMaxY()
                                        - stage.getHeight()
                        )
                )
        );
    }
}
