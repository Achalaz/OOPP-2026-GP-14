package lk.ac.ruhuna.ftms.util;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

/** One screen's database work. All completion handlers run on the JavaFX thread. */
public final class TimetableTaskRunner {
    private final BooleanProperty busy;
    private final Label status;
    private final SessionManager.SessionIdentity identity;
    private final Runnable clearData;
    private final PauseTransition timeout = new PauseTransition(Duration.seconds(30));
    private Task<?> active;
    private boolean disposed;
    private Scene scene;
    private Window window;
    private final ChangeListener<Boolean> showingListener = (observable, previous, showing) -> {
        if (!showing) dispose();
    };
    private final ChangeListener<Window> windowListener = (observable, previous, current) -> {
        // Replacing a Stage's Scene leaves the old root in its old Scene.
        if (previous != null && current == null) dispose();
        else if (!disposed) watchWindow(current);
    };

    public TimetableTaskRunner(Parent root, BooleanProperty busy, Label status,
                               SessionManager.SessionIdentity identity, Runnable clearData) {
        this.busy = busy;
        this.status = status;
        this.identity = identity;
        this.clearData = clearData;
        root.sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null && current == null) dispose();
            else if (current != null && !disposed) watchScene(current);
        });
        if (root.getScene() != null) watchScene(root.getScene());
    }

    private void watchScene(Scene current) {
        if (scene != null) scene.windowProperty().removeListener(windowListener);
        scene = current;
        scene.windowProperty().addListener(windowListener);
        watchWindow(scene.getWindow());
    }

    private void watchWindow(Window current) {
        if (window != null) window.showingProperty().removeListener(showingListener);
        window = current;
        if (window != null) window.showingProperty().addListener(showingListener);
    }

    public <T> void run(String message, Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (disposed || active != null) return;
        if (!SessionManager.isCurrentSession(identity)) {
            clearData.run();
            status.setText("Your session has ended. Please sign in again.");
            return;
        }
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                SessionManager.requireSession(identity);
                return work.call();
            }
        };
        active = task;
        busy.set(true);
        status.setText(message);
        task.setOnSucceeded(event -> {
            if (!finish(task)) return;
            try { success.accept(task.getValue()); }
            catch (RuntimeException error) { failure.accept(error); }
        });
        task.setOnFailed(event -> {
            if (finish(task)) failure.accept(task.getException());
        });
        task.setOnCancelled(event -> {
            if (finish(task)) {
                clearData.run();
                status.setText("Operation cancelled. Refresh to check the saved timetable.");
            }
        });
        // Modal failure dialogs cannot be shown during an animation pulse.
        timeout.setOnFinished(event -> Platform.runLater(() -> {
            if (active != task) return;
            boolean showFailure = finish(task);
            task.cancel(true);
            if (showFailure) failure.accept(new IllegalStateException(
                    "The timetable request timed out. Check the database connection and refresh before retrying a change."));
        }));
        timeout.playFromStart();
        Thread worker = new Thread(task, "ftms-timetable-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean finish(Task<?> task) {
        if (disposed || active != task) return false;
        active = null;
        timeout.stop();
        busy.set(false);
        if (!SessionManager.isCurrentSession(identity)) {
            clearData.run();
            status.setText("Your session has ended. Please sign in again.");
            return false;
        }
        return true;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (scene != null) scene.windowProperty().removeListener(windowListener);
        watchWindow(null);
        timeout.stop();
        Task<?> task = active;
        active = null;
        if (task != null) task.cancel(true);
        busy.set(false);
        clearData.run();
        status.setText("Timetable closed.");
    }

    /** Keep long database diagnostics in the dialog so the status bar stays readable. */
    public static String statusMessage(String message) {
        int details = message.indexOf("\n\nDetails:");
        return (details < 0 ? message : message.substring(0, details)).replace("\n\n", " ");
    }

    public static String errorMessage(String context, Throwable error) {
        if (error == null) return context;
        Throwable cause = error;
        SQLException databaseError = null;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        while (seen.add(cause)) {
            if (cause instanceof SQLException sql) databaseError = sql;
            if (cause.getCause() == null || seen.contains(cause.getCause())) break;
            cause = cause.getCause();
        }
        String detail = cause.getMessage();
        String nextStep = "";
        if (databaseError != null) {
            String state = databaseError.getSQLState();
            if (databaseError instanceof SQLTimeoutException || (state != null && state.startsWith("08"))) {
                nextStep = "The database could not complete the request. Check the connection and click Refresh before retrying a change.";
            } else if (state != null && state.startsWith("28")) {
                nextStep = "The database rejected the application's sign-in. Ask your administrator to check database access.";
            } else if (state != null && state.startsWith("40")) {
                nextStep = "Another database operation interrupted this request. Click Refresh, then try again.";
            } else {
                nextStep = "Click Refresh to check the latest timetable. If the problem continues, share the details below with your administrator.";
            }
        }
        return context + (nextStep.isEmpty() ? "" : "\n\n" + nextStep)
                + "\n\nDetails: " + (detail == null || detail.isBlank() ? cause.getClass().getSimpleName() : detail);
    }
}
