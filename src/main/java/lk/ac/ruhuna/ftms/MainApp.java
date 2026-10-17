
package lk.ac.ruhuna.ftms;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        URL fxmlUrl = MainApp.class.getResource(
                "/fxml/lecturer_dashboard.fxml"
        );

        if (fxmlUrl == null) {
            throw new IllegalStateException(
                    "lecturer_dashboard.fxml not found!"
            );
        }

        // Load FXML text for visual preview only
        String fxml;

        try (var input = fxmlUrl.openStream()) {
            fxml = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        // Disable controller and button actions in preview
        fxml = fxml.replaceAll(
                "\\s+fx:controller=\"[^\"]*\"",
                ""
        );

        fxml = fxml.replaceAll(
                "\\s+onAction=\"#[^\"]*\"",
                ""
        );

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(fxmlUrl);

        Parent root = loader.load(
                new ByteArrayInputStream(
                        fxml.getBytes(StandardCharsets.UTF_8)
                )
        );

        Scene scene = new Scene(root, 1250, 800);

        stage.setTitle("FTMS - Lecturer Dashboard Preview");
        stage.setScene(scene);

        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
