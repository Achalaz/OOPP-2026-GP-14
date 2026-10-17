
package lk.ac.ruhuna.ftms;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lk.ac.ruhuna.ftms.util.SessionManager;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Clear previous login session
        SessionManager.logout();

        // Load login screen
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/fxml/login.fxml")
        );

        Parent root = loader.load();

        // Get available screen size
        Rectangle2D screen =
                Screen.getPrimary().getVisualBounds();

        // Keep window within available screen
        double width = Math.min(1100, screen.getWidth() - 40);
        double height = Math.min(720, screen.getHeight() - 70);

        Scene scene = new Scene(root, width, height);

        stage.setTitle("FTMS - Login");
        stage.setScene(scene);

        // Normal resizable window
        stage.setResizable(true);
        stage.setFullScreen(false);

        // Logout when application closes
        stage.setOnCloseRequest(event ->
                SessionManager.logout()
        );

        stage.show();

        // Position window at screen center
        stage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
