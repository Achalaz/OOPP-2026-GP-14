
package lk.ac.ruhuna.ftms;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import lk.ac.ruhuna.ftms.util.SessionManager;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Load Login Screen
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/fxml/login.fxml")
        );

        Scene scene = new Scene(loader.load(), 1200, 700);

        stage.setTitle("FTMS - Login");
        stage.setScene(scene);

        stage.setMinWidth(900);
        stage.setMinHeight(600);

        stage.show();
    }

    @Override
    public void stop() {
        SessionManager.logout();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
