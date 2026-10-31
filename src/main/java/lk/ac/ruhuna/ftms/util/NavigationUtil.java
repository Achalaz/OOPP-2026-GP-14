
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

    // =====================================
    // NOTICES ACCESS CONTROL
    // =====================================

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

    // =====================================
    // DASHBOARD RESOURCE
    // =====================================

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

    // =====================================
    // BASIC NAVIGATION
    // =====================================

    public static void navigate(
            Node source,
            String resource,
            String title) throws IOException {

        navigate(
                source,
                resource,
                title,
                controller -> {}
        );
    }

    // =====================================
    // SECURE NAVIGATION
    // =====================================

    /**
     * Configures screen context without a
     * second Stage or a separate navigation path.
     */
    public static void navigate(
            Node source,
            String resource,
            String title,
            java.util.function.Consumer<Object> configure
    ) throws IOException {

        if ("/fxml/dashboard.fxml".equals(resource)) {
            resource = dashboardResource();
        }

        requireNoticeAccess(resource);

        // Admin Course Management
        if ("/fxml/courses.fxml".equals(resource)
                && !CourseService.isCurrentUserAdmin()) {

            throw new SecurityException(
                    "Active administrator access is required to manage courses."
            );
        }

        var user = SessionManager.getCurrentUser();

        // Student results redirect
        if ("/fxml/results.fxml".equals(resource)
                && user != null
                && "STUDENT".equals(user.getRole())) {

            resource = "/fxml/student_results.fxml";
        }

        // Lecturer academic screens
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
                    "An active assigned lecturer is required for academic management."
            );
        }

        // Admin full-transcript access applies to GPA only; reports keep their existing scope.
        if ("/fxml/gpa.fxml".equals(resource)
                && (user == null || !user.isActive()
                || !Set.of("ADMIN", "LECTURER", "STUDENT").contains(user.getRole()))) {
            throw new SecurityException("An active Admin, lecturer or student account is required for GPA.");
        }

        if ("/fxml/reports.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !Set.of(
                "LECTURER",
                "STUDENT"
        ).contains(user.getRole()))) {

            throw new SecurityException(
                    "An active lecturer or student account is required for this academic view."
            );
        }

        // Required roles
        String requiredRole = switch (resource) {

            case "/fxml/admin_enrollments.fxml",
                 "/fxml/course_offerings.fxml",
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

        // =====================================
        // TIMETABLE ACCESS
        // =====================================

        String timetableRole = switch (resource) {

            case "/fxml/admin_timetable.fxml" ->
                    "ADMIN";

            case "/fxml/timetable.fxml" ->
                    "TECHNICAL_OFFICER";

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

        // =====================================
        // ATTENDANCE MANAGEMENT ACCESS
        // =====================================

        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(
                user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required "
                            + "for attendance management."
            );
        }

        // =====================================
        // STEP 20: MEDICAL MANAGEMENT ACCESS
        // =====================================

        if ("/fxml/medical.fxml".equals(resource)
                && (user == null
                || !user.isActive()
                || !"TECHNICAL_OFFICER".equals(
                user.getRole()))) {

            throw new SecurityException(
                    "Active Technical Officer access is required "
                            + "for medical management."
            );
        }

        // =====================================
        // STUDENT-ONLY SCREENS
        // =====================================

        Set<String> studentViews = Set.of(
                "/fxml/student_dashboard.fxml",
                "/fxml/student_attendance.fxml",
                "/fxml/student_eligibility.fxml",
                "/fxml/student_medical.fxml",
                "/fxml/student_courses.fxml",
                "/fxml/student_assessments.fxml",
                "/fxml/student_materials.fxml",
                "/fxml/student_results.fxml",
                "/fxml/student_timetable.fxml"
        );

        if (studentViews.contains(resource)
                && (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole()))) {

            throw new SecurityException(
                    "An active student session is required."
            );
        }

        // Student workspace protection
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
                    "Use your student workspace to access your own records."
            );
        }

        // =====================================
        // LECTURER ACCESS
        // =====================================

        if (user != null
                && "LECTURER".equals(user.getRole())
                && !Set.of(
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
        ).contains(resource)) {

            throw new SecurityException(
                    "Use the lecturer workspace for authorized course records."
            );
        }

        // =====================================
        // LOAD FXML SCREEN
        // =====================================

        URL location =
                NavigationUtil.class.getResource(resource);

        if (location == null) {

            throw new IOException(
                    "Screen not found: " + resource
            );
        }

        FXMLLoader loader = new FXMLLoader(location);

        Parent root = loader.load();

        configure.accept(loader.getController());

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

        // This controller loads its table/dropdowns only after service injection.
        // Both dashboard entry points must also initialize lecturer assignments.
        if ("/fxml/course_offerings.fxml".equals(resource)) {
            CourseOfferingController controller = loader.getController();
            controller.setCourseOfferingService(new CourseOfferingService(
                    new CourseOfferingDAO(), CourseService::isCurrentUserAdmin));
            controller.setLecturerAssignmentService(new LecturerAssignmentService(
                    new LecturerCourseAssignmentDAO()));
        }

        Scene scene = source.getScene();

        // =====================================
        // PRESERVE WINDOW SIZE
        // =====================================

        boolean preserveWindowSize =

                timetableRole != null

                        || lecturerAcademicViews.contains(resource)

                        || Set.of(
                        "/fxml/gpa.fxml",
                        "/fxml/reports.fxml",
                        "/fxml/lecturer_dashboard.fxml"
                ).contains(resource)

                        || studentViews.contains(resource)

                        || "/fxml/admin_dashboard.fxml".equals(resource)

                        || "/fxml/admin_enrollments.fxml".equals(resource)

                        || "/fxml/courses.fxml".equals(resource)

                        || "/fxml/course_offerings.fxml".equals(resource)

                        || Set.of(
                        "adminTimetableRoot",
                        "timetableRoot",
                        "studentTimetableRoot"
                ).contains(
                        String.valueOf(
                                scene.getRoot().getId()
                        )
                )

                        || "courseOfferingsRoot".equals(
                        scene.getRoot().getId()
                )

                        || "coursesRoot".equals(
                        scene.getRoot().getId()
                )

                        || "/fxml/technical_officer_dashboard.fxml".equals(resource)

                        || "/fxml/attendance.fxml".equals(resource)

                        // Step 20 - Medical window size
                        || "/fxml/medical.fxml".equals(resource)

                        || "/fxml/login.fxml".equals(resource)

                        || "/fxml/notices.fxml".equals(resource)

                        || "/fxml/view_notices.fxml".equals(resource)

                        || "noticesRoot".equals(
                        scene.getRoot().getId()
                )

                        || "viewNoticesRoot".equals(
                        scene.getRoot().getId()
                )

                        || "studentRoot".equals(
                        scene.getRoot().getId()
                )

                        || "studentEligibilityRoot".equals(
                        scene.getRoot().getId()
                )

                        || "studentAttendanceRoot".equals(
                        scene.getRoot().getId()
                )

                        || "technicalOfficerRoot".equals(
                        scene.getRoot().getId()
                )

                        || "attendanceRoot".equals(
                        scene.getRoot().getId()
                )

                        || "medicalRoot".equals(
                        scene.getRoot().getId()
                );

        scene.setRoot(root);

        // =====================================
        // SET STAGE TITLE AND SIZE
        // =====================================

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

    // =====================================
    // FIT TIMETABLE TO SCREEN
    // =====================================

    /**
     * Clamp window dimensions to current display.
     */
    private static void fitTimetableWindow(Stage stage) {

        if (stage.isMaximized() || stage.isFullScreen()) {
            return;
        }

        var screens = Screen.getScreensForRectangle(
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight()
        );

        Rectangle2D bounds = (
                screens.isEmpty()
                        ? Screen.getPrimary()
                        : screens.getFirst()
        ).getVisualBounds();

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
                                bounds.getMaxX() - stage.getWidth()
                        )
                )
        );

        stage.setY(
                Math.max(
                        bounds.getMinY(),
                        Math.min(
                                stage.getY(),
                                bounds.getMaxY() - stage.getHeight()
                        )
                )
        );
    }
}
