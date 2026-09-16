package lk.ac.ruhuna.ftms;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        Label label = new Label("Faculty Technology Management System");

        StackPane root = new StackPane(label);

        Scene scene = new Scene(root, 800, 500);

        stage.setTitle("FTMS - Group 14");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
