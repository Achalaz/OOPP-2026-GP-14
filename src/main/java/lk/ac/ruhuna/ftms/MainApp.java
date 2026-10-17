package lk.ac.ruhuna.ftms;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/fxml/admin_dashboard.fxml")
        );

        Scene scene = new Scene(loader.load(), 1200, 750);

        stage.setTitle("FTMS - Admin Dashboard");
        stage.setScene(scene);

        stage.setMinWidth(950);
        stage.setMinHeight(650);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}