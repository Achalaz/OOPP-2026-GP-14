package lk.ac.ruhuna.ftms.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
        var user = SessionManager.getCurrentUser();
        if ("/fxml/attendance.fxml".equals(resource)
                && (user == null || !user.isActive() || !"TECHNICAL_OFFICER".equals(user.getRole()))) {
            throw new SecurityException("Active Technical Officer access is required for attendance management.");
        }
        if (user != null && "LECTURER".equals(user.getRole())
                && !java.util.Set.of("/fxml/lecturer_dashboard.fxml", "/fxml/lecturer_workspace.fxml",
                    "/fxml/marks_entry.fxml", "/fxml/profile.fxml", "/fxml/view_notices.fxml",
                    "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the lecturer workspace for authorized course records.");
        }
        if (user != null && "TECHNICAL_OFFICER".equals(user.getRole())
                && !java.util.Set.of("/fxml/technical_officer_dashboard.fxml",
                    "/fxml/attendance.fxml", "/fxml/profile.fxml", "/fxml/view_notices.fxml",
                    "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the Technical Officer workspace for authorized department features.");
        }
        URL location = NavigationUtil.class.getResource(resource);
        if (location == null) {
            throw new IOException("Screen not found: " + resource);
        }

        Parent root = new FXMLLoader(location).load();
        Scene scene = source.getScene();
        // Responsive portals and logout retain MainApp's screen-bounded window size.
        boolean preserveWindowSize = "/fxml/student_dashboard.fxml".equals(resource)
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
