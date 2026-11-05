package lk.ac.ruhuna.ftms.controller;
import javafx.fxml.FXML;
/** Compatibility entry point: the old resource now includes the primary workspace. */
public class CourseMaterialsController {
    @FXML private LecturerWorkspaceController workspaceController;
    @FXML public void initialize() {
        workspaceController.configure("materials", "Course Materials");
    }
}
