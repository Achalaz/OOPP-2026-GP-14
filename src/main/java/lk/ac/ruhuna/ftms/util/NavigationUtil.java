package lk.ac.ruhuna.ftms.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
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

public final class NavigationUtil {

    private NavigationUtil() {
    }

    private static void requireNoticeAccess(String resource) {
        var user = SessionManager.getCurrentUser();
        if ("/fxml/notices.fxml".equals(resource)
                && (user == null || !user.isActive() || !"ADMIN".equals(user.getRole()))) {
            throw new SecurityException("Active administrator access is required to manage notices.");
        }
        if ("/fxml/view_notices.fxml".equals(resource)
                && (user == null || !user.isActive() || user.getRole() == null
                || !java.util.Set.of("ADMIN", "LECTURER", "STUDENT", "TECHNICAL_OFFICER").contains(user.getRole()))) {
            throw new SecurityException("Please sign in with an active FTMS account to view notices.");
        }
    }

    public static String dashboardResource() {
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive()) throw new SecurityException("Please sign in.");
        return switch (user.getRole()) {
            case "LECTURER" -> "/fxml/lecturer_dashboard.fxml";
            case "ADMIN" -> "/fxml/admin_dashboard.fxml";
            case "STUDENT" -> "/fxml/student_dashboard.fxml";
            case "TECHNICAL_OFFICER" -> "/fxml/technical_officer_dashboard.fxml";
            default -> "/fxml/dashboard.fxml";
        };
    }

    public static void navigate(Node source, String resource, String title) throws IOException {
        if ("/fxml/dashboard.fxml".equals(resource)) resource = dashboardResource();
        requireNoticeAccess(resource);
        if ("/fxml/courses.fxml".equals(resource) && !CourseService.isCurrentUserAdmin()) {
            throw new SecurityException("Active administrator access is required to manage courses.");
        }
        if ("/fxml/course_offerings.fxml".equals(resource) && !CourseService.isCurrentUserAdmin()) {
            throw new SecurityException("Active administrator access is required to manage course offerings.");
        }
        var user = SessionManager.getCurrentUser();
        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null || !user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole()))) {
            throw new SecurityException("Active Technical Officer access is required for attendance management.");
        }
        if (user != null && "LECTURER".equals(user.getRole())
                && !java.util.Set.of("/fxml/lecturer_dashboard.fxml", "/fxml/lecturer_workspace.fxml",
                    "/fxml/marks_entry.fxml", "/fxml/profile.fxml", "/fxml/view_notices.fxml",
                    "/fxml/assessments.fxml", "/fxml/results.fxml", "/fxml/eligibility.fxml",
                    "/fxml/reports.fxml", "/fxml/gpa.fxml",
                    "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the lecturer workspace for authorized course records.");
        }
        if (user != null && "TECHNICAL_OFFICER".equals(user.getRole())
                && !java.util.Set.of("/fxml/technical_officer_dashboard.fxml",
                    "/fxml/attendance.fxml", "/fxml/profile.fxml", "/fxml/view_notices.fxml",
                    "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the Technical Officer workspace for authorized department features.");
        }
        if (user != null && "STUDENT".equals(user.getRole())
                && !java.util.Set.of("/fxml/student_dashboard.fxml", "/fxml/student_courses.fxml",
                    "/fxml/student_attendance.fxml", "/fxml/student_medical.fxml",
                    "/fxml/student_results.fxml", "/fxml/profile.fxml", "/fxml/view_notices.fxml",
                    "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the student dashboard for authorized academic records.");
        }
        URL location = NavigationUtil.class.getResource(resource);
        if (location == null) {
            throw new IOException("Screen not found: " + resource);
        }

        FXMLLoader loader = new FXMLLoader(location);
        Parent root = loader.load();
        if ("/fxml/courses.fxml".equals(resource)) {
            CourseController controller = loader.getController();
            controller.setCourseService(new CourseService(new CourseDAO(), CourseService::isCurrentUserAdmin));
        }
        if ("/fxml/course_offerings.fxml".equals(resource)) {
            CourseOfferingController controller = loader.getController();
            controller.setCourseOfferingService(new CourseOfferingService(
                    new CourseOfferingDAO(), CourseService::isCurrentUserAdmin));
            controller.setLecturerAssignmentService(new LecturerAssignmentService(new LecturerCourseAssignmentDAO()));
        }
        Scene scene = source.getScene();
        // Responsive portals and logout retain MainApp's screen-bounded window size.
        boolean preserveWindowSize = "/fxml/student_dashboard.fxml".equals(resource)
                || "/fxml/admin_dashboard.fxml".equals(resource)
                || "/fxml/courses.fxml".equals(resource)
                || "/fxml/course_offerings.fxml".equals(resource)
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
                || "technicalOfficerRoot".equals(scene.getRoot().getId())
                || "attendanceRoot".equals(scene.getRoot().getId());
        scene.setRoot(root);
        if (scene.getWindow() instanceof Stage stage) {
            stage.setTitle(title);
            if (!preserveWindowSize) stage.sizeToScene();
        }
    }
}
