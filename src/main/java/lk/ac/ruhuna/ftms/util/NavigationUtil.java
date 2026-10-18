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

    public static String dashboardResource() {
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive()) throw new SecurityException("Please sign in.");
        return switch (user.getRole()) {
            case "LECTURER" -> "/fxml/lecturer_dashboard.fxml";
            case "ADMIN" -> "/fxml/admin_dashboard.fxml";
            case "STUDENT" -> "/fxml/student_dashboard.fxml";
            default -> "/fxml/dashboard.fxml";
        };
    }

    public static void navigate(Node source, String resource, String title) throws IOException {
        if ("/fxml/dashboard.fxml".equals(resource)) resource = dashboardResource();
        var user = SessionManager.getCurrentUser();
        if (user != null && "LECTURER".equals(user.getRole())
                && !java.util.Set.of("/fxml/lecturer_dashboard.fxml", "/fxml/lecturer_workspace.fxml",
                    "/fxml/marks_entry.fxml", "/fxml/profile.fxml", "/fxml/login.fxml").contains(resource)) {
            throw new SecurityException("Use the lecturer workspace for authorized course records.");
        }
        URL location = NavigationUtil.class.getResource(resource);
        if (location == null) {
            throw new IOException("Screen not found: " + resource);
        }

        Parent root = new FXMLLoader(location).load();
        Scene scene = source.getScene();
        // The responsive student UI keeps MainApp's screen-bounded login window size,
        // including on logout; other dashboards retain their existing sizing behavior.
        boolean preserveWindowSize = "/fxml/student_dashboard.fxml".equals(resource)
                || "studentRoot".equals(scene.getRoot().getId());
        scene.setRoot(root);
        if (scene.getWindow() instanceof Stage stage) {
            stage.setTitle(title);
            if (!preserveWindowSize) stage.sizeToScene();
        }
    }
}
