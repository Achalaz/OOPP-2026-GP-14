package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.geometry.Rectangle2D;
import javafx.stage.FileChooser;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.ProfileService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Callable;

public class ProfileController {
    @FXML private BorderPane profileRoot;
    @FXML private ScrollPane contentScroll;
    @FXML private GridPane profileGrid;
    @FXML private VBox summaryCard;
    @FXML private VBox detailsCard;
    @FXML private StackPane defaultAvatar;
    @FXML private ImageView profileImage;
    @FXML private Button changePictureButton;
    @FXML private Button removePictureButton;
    @FXML private Label pictureHintLabel;
    @FXML private Label summaryNameLabel;
    @FXML private Label summaryEmailLabel;
    @FXML private Label summaryRoleLabel;
    @FXML private Label statusLabel;
    @FXML private TextField usernameField;
    @FXML private TextField roleField;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private Button saveButton;
    @FXML private Button resetButton;
    @FXML private Button refreshButton;
    @FXML private Button dashboardButton;
    @FXML private Button logoutButton;
    @FXML private ProgressIndicator progressIndicator;

    private final ProfileService profileService;
    private final SessionIdentity identity;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty();
    private final BooleanProperty picturePresent = new SimpleBooleanProperty();
    private User savedProfile;
    private Task<ProfileView> activeTask;
    private boolean disposed;

    public ProfileController() {
        this(new ProfileService());
    }

    public ProfileController(ProfileService profileService) {
        this.profileService = Objects.requireNonNull(profileService);
        identity = SessionManager.requireSession();
    }

    @FXML
    public void initialize() {
        SessionManager.requireSession(identity);
        fullNameField.disableProperty().bind(busy.or(loaded.not()));
        emailField.disableProperty().bind(busy.or(loaded.not()));
        saveButton.disableProperty().bind(busy.or(loaded.not()));
        resetButton.disableProperty().bind(busy.or(loaded.not()));
        changePictureButton.disableProperty().bind(busy.or(loaded.not()));
        removePictureButton.disableProperty().bind(busy.or(loaded.not()).or(picturePresent.not()));
        profileImage.setClip(new Circle(40, 40, 40));
        refreshButton.disableProperty().bind(busy);
        dashboardButton.disableProperty().bind(busy);
        logoutButton.disableProperty().bind(busy);
        progressIndicator.visibleProperty().bind(busy);
        progressIndicator.managedProperty().bind(busy);
        contentScroll.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                arrangeCards(bounds.getWidth() < 800));
        profileRoot.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (oldScene != null && scene == null) {
                disposed = true;
                if (activeTask != null) {
                    activeTask.cancel(true);
                }
            }
        });
        handleRefresh();
    }

    private void arrangeCards(boolean stacked) {
        profileGrid.getColumnConstraints().get(0).setPercentWidth(stacked ? 100 : 32);
        profileGrid.getColumnConstraints().get(1).setPercentWidth(stacked ? 0 : 68);
        GridPane.setColumnIndex(detailsCard, stacked ? 0 : 1);
        GridPane.setRowIndex(detailsCard, stacked ? 1 : 0);
        // Keep a single column when stacked, including the horizontal gap.
        profileGrid.setHgap(stacked ? 0 : 24);
    }

    @FXML
    private void handleRefresh() {
        if (busy.get() || disposed) { return; }
        loaded.set(false);
        runTask("Loading your profile...", profileService::getMyProfile, "Your profile is up to date.");
    }

    @FXML
    private void handleSave() {
        if (busy.get() || !loaded.get() || disposed) { return; }
        // Copy field values on the FX thread; the worker never reads JavaFX controls.
        String fullName = fullNameField.getText();
        String email = emailField.getText();
        runTask("Saving your changes...", () -> profileService.updateMyProfile(fullName, email),
                "Your profile was saved successfully.");
    }

    @FXML
    private void handleChangePicture() {
        if (busy.get() || !loaded.get() || disposed) { return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose your profile picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "JPG and PNG pictures", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
        java.io.File selected = chooser.showOpenDialog(profileRoot.getScene().getWindow());
        if (selected != null) { uploadPicture(selected.toPath()); }
    }

    // FileChooser and tests both enter the same asynchronous upload path.
    void uploadPicture(Path selected) {
        if (busy.get() || !loaded.get() || disposed) { return; }
        runTask("Saving your profile picture...", () -> profileService.changeMyProfilePicture(selected),
                "Your profile picture was saved successfully.", false);
    }

    @FXML
    private void handleRemovePicture() {
        if (busy.get() || !loaded.get() || disposed) { return; }
        runTask("Removing your profile picture...", profileService::removeMyProfilePicture,
                "Your profile picture was removed.", false);
    }

    @FXML
    private void handleReset() {
        if (savedProfile != null && !busy.get()) {
            display(savedProfile);
            showMessage("Unsaved changes were reset.", "info");
        }
    }

    @FXML
    private void handleDashboard() {
        if (busy.get()) { return; }
        navigate("/fxml/dashboard.fxml", "FTMS - Dashboard", false);
    }

    @FXML
    private void handleLogout() {
        if (busy.get()) { return; }
        navigate("/fxml/login.fxml", "FTMS - Login", true);
    }

    private void navigate(String resource, String title, boolean logout) {
        try {
            NavigationUtil.navigate(profileRoot, resource, title);
            if (logout) { SessionManager.logout(); }
        } catch (IOException error) {
            showMessage("Cannot open the requested screen. Please retry.", "error");
            error.printStackTrace();
        }
    }

    private void runTask(String message, Callable<User> work, String successMessage) {
        runTask(message, work, successMessage, true);
    }

    private record ProfileView(User user, Image picture, String pictureHint) {}

    private ProfileView loadPicture(User user) throws IOException {
        String hint = "JPG or PNG. Maximum 2 MB.";
        try {
            byte[] bytes = profileService.loadMyProfilePicture(user.getProfilePicture());
            if (bytes != null) {
                Image picture = new Image(new ByteArrayInputStream(bytes), 160, 160, true, true);
                if (!picture.isError()) { return new ProfileView(user, picture, hint); }
            } else if (user.getProfilePicture() == null) {
                return new ProfileView(user, null, hint);
            }
        } catch (IOException | IllegalArgumentException error) {
            // A missing/corrupt/legacy picture must not make personal details unusable.
        }
        return new ProfileView(user, null, "Saved picture unavailable. Choose a new JPG or PNG (up to 2 MB).");
    }

    private void runTask(String message, Callable<User> work, String successMessage, boolean replaceDetails) {
        if (!SessionManager.isCurrentSession(identity)) {
            loaded.set(false);
            showMessage("Your session has ended. Please log out and sign in again.", "error");
            return;
        }
        busy.set(true);
        showMessage(message, "info");
        Task<ProfileView> task = new Task<>() {
            @Override protected ProfileView call() throws Exception { return loadPicture(work.call()); }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            busy.set(false);
            if (!acceptResult(task)) { return; }
            ProfileView view = task.getValue();
            savedProfile = view.user();
            if (replaceDetails) { display(savedProfile); }
            else { displaySummary(savedProfile); }
            showPicture(view);
            loaded.set(true);
            showMessage(successMessage, "success");
        });
        task.setOnFailed(event -> {
            busy.set(false);
            if (!acceptResult(task)) { return; }
            Throwable error = task.getException();
            if (error instanceof SecurityException) { loaded.set(false); }
            showMessage(errorMessage(error), "error");
            if (error instanceof SQLException) { error.printStackTrace(); }
        });
        task.setOnCancelled(event -> busy.set(false));
        Thread worker = new Thread(task, "ftms-profile");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean acceptResult(Task<ProfileView> task) {
        if (disposed || task != activeTask) { return false; }
        if (!SessionManager.isCurrentSession(identity)) {
            loaded.set(false);
            showMessage("Your session has ended. Please log out and sign in again.", "error");
            return false;
        }
        return true;
    }

    private void display(User user) {
        usernameField.setText(user.getUsername());
        roleField.setText(user.getRole());
        fullNameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        displaySummary(user);
    }

    private void displaySummary(User user) {
        summaryNameLabel.setText(user.getFullName());
        summaryEmailLabel.setText(user.getEmail() == null || user.getEmail().isBlank()
                ? "Add your email address" : user.getEmail());
        summaryRoleLabel.setText(user.getRole().replace('_', ' '));
    }

    private void showPicture(ProfileView view) {
        profileImage.setImage(view.picture());
        if (view.picture() != null) {
            double side = Math.min(view.picture().getWidth(), view.picture().getHeight());
            profileImage.setViewport(new Rectangle2D((view.picture().getWidth() - side) / 2,
                    (view.picture().getHeight() - side) / 2, side, side));
        }
        defaultAvatar.setVisible(view.picture() == null);
        profileImage.setVisible(view.picture() != null);
        picturePresent.set(view.user().getProfilePicture() != null);
        pictureHintLabel.setText(view.pictureHint());
    }

    private void showMessage(String message, String kind) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-info", "status-success", "status-error");
        statusLabel.getStyleClass().add("status-" + kind);
    }

    static String errorMessage(Throwable error) {
        if (error instanceof IllegalArgumentException || error instanceof SecurityException
                || error instanceof IllegalStateException) {
            return error.getMessage();
        }
        if (error instanceof SQLException sql) {
            if (sql.getErrorCode() == 1054) {
                return "Profile pictures need the users.profile_picture column. Ask your administrator to apply the supplied migration.";
            }
            if (sql.getErrorCode() == 1062) {
                return "That email address is already in use. Please enter a different email.";
            }
            if ("22001".equals(sql.getSQLState())) {
                return "Your name or email is too long. Shorten it and try again.";
            }
        }
        if (error instanceof IOException) {
            return "Unable to read or store the picture. Check that the file and the application picture directory are accessible.";
        }
        return "Unable to complete the profile request. Check the database connection and retry."
                + " If a save was interrupted, refresh to check your saved details.";
    }
}
