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

    public static void navigate(Node source, String resource, String title) throws IOException {
        URL location = NavigationUtil.class.getResource(resource);
        if (location == null) {
            throw new IOException("Screen not found: " + resource);
        }

        Parent root = new FXMLLoader(location).load();
        Scene scene = source.getScene();
        scene.setRoot(root);
        if (scene.getWindow() instanceof Stage stage) {
            stage.setTitle(title);
            stage.sizeToScene();
        }
    }
}
