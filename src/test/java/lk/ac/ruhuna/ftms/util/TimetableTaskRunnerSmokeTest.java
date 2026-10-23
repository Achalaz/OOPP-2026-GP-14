package lk.ac.ruhuna.ftms.util;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import lk.ac.ruhuna.ftms.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit JavaFX runner for task races and modal timeout errors. No database access. */
public final class TimetableTaskRunnerSmokeTest {
    private static Stage stage;
    private static final AtomicInteger checks = new AtomicInteger();
    private static final ConcurrentLinkedQueue<Throwable> uncaught = new ConcurrentLinkedQueue<>();

    private record Screen(BorderPane root, SimpleBooleanProperty busy, Label status, Button refresh,
                          TimetableTaskRunner tasks, AtomicInteger clears, SessionManager.SessionIdentity identity) {}

    public static void main(String[] args) throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        await(ready, "JavaFX starts");
        Platform.setImplicitExit(false);
        try {
            fx(() -> {
                Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
                stage = new Stage(); stage.setOpacity(0);
                stage.setScene(new Scene(new BorderPane(), 480, 320)); stage.show();
                return null;
            });
            for (String scenario : args.length == 0
                    ? List.of("timeout", "scene", "success", "failure", "cancel", "queued_timeout") : List.of(args)) {
                try {
                    switch (scenario) {
                        case "timeout" -> timeoutDialog();
                        case "scene" -> sceneReplacement();
                        case "success" -> successAndFollowup();
                        case "failure" -> failureAndRecovery();
                        case "cancel" -> cancelAndRetry();
                        case "queued_timeout" -> queuedTimeoutAfterNavigation();
                        default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
                    }
                } catch (Throwable error) { uncaught.add(error); }
            }
            if (!uncaught.isEmpty()) {
                var error = new AssertionError("Timetable async checks failed");
                uncaught.forEach(error::addSuppressed);
                throw error;
            }
            System.out.println("Timetable async JavaFX: " + checks.get() + " checks passed; no database access.");
        } finally {
            fx(() -> { stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static Screen screen() throws Exception {
        return fx(() -> {
            SessionManager.startSession(new User(3, "async-fixture", "Async Fixture", null, "STUDENT", true));
            var root = new BorderPane();
            stage.getScene().setRoot(root);
            var busy = new SimpleBooleanProperty(); var status = new Label(); var refresh = new Button("Refresh");
            refresh.disableProperty().bind(busy); root.setTop(status); root.setCenter(refresh);
            var clears = new AtomicInteger(); var identity = SessionManager.requireSession();
            return new Screen(root, busy, status, refresh,
                    new TimetableTaskRunner(root, busy, status, identity, clears::incrementAndGet), clears, identity);
        });
    }

    private static void timeoutDialog() throws Exception {
        var s = screen(); var release = new CountDownLatch(1); var handled = new CountDownLatch(1);
        var dialogError = new AtomicReference<Throwable>();
        try {
            fx(() -> {
                ((PauseTransition) field(s.tasks, "timeout")).setDuration(Duration.millis(80));
                s.tasks.run("Loading timetable...", () -> { release.await(); return 1; }, value -> {
                    throw new AssertionError("Timed-out work must not update controls");
                }, error -> {
                    var alert = new Alert(Alert.AlertType.ERROR, TimetableTaskRunner.errorMessage("Could not load timetable.", error), ButtonType.OK);
                    alert.initOwner(stage);
                    Platform.runLater(() -> ((Button) alert.getDialogPane().lookupButton(ButtonType.OK)).fire());
                    try {
                        check(Platform.isFxApplicationThread() && !s.refresh.isDisabled(), "timeout re-enables controls on FX thread");
                        s.status.setText(TimetableTaskRunner.statusMessage(alert.getContentText()));
                        alert.showAndWait();
                        check(alert.getContentText().contains("timed out"), "timeout dialog explains failure");
                    } catch (Throwable failure) { dialogError.set(failure); }
                    finally { alert.close(); handled.countDown(); }
                });
                return null;
            });
            await(handled, "timeout delivers an error");
            check(dialogError.get() == null, "timeout may show a modal error: " + dialogError.get());
            fx(() -> { check(!s.status.getText().startsWith("Loading"), "timeout clears loading text"); return null; });
        } finally { release.countDown(); fx(() -> { s.tasks.dispose(); return null; }); }
    }

    private static void sceneReplacement() throws Exception {
        var s = screen(); var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var callbacks = new AtomicInteger(); var worker = new AtomicReference<Thread>();
        try {
            fx(() -> {
                s.tasks.run("Loading timetable...", () -> {
                    worker.set(Thread.currentThread()); started.countDown(); awaitIgnoringInterrupt(release); return 1;
                }, value -> callbacks.incrementAndGet(), error -> callbacks.incrementAndGet());
                return null;
            });
            await(started, "work starts before scene replacement");
            fx(() -> {
                stage.setScene(new Scene(new BorderPane(), 480, 320));
                check(!s.refresh.isDisabled() && s.clears.get() == 1, "replacing the scene cancels loading and clears the old screen");
                return null;
            });
            release.countDown(); worker.get().join(2000);
            fx(() -> { check(callbacks.get() == 0, "scene replacement discards late callbacks"); return null; });
        } finally { release.countDown(); fx(() -> { s.tasks.dispose(); return null; }); }
    }

    private static void successAndFollowup() throws Exception {
        var s = screen(); var release = new CountDownLatch(1); var done = new CountDownLatch(1);
        var duplicate = new AtomicInteger();
        try {
            fx(() -> {
                s.tasks.run("Loading timetable...", () -> { release.await(); return Platform.isFxApplicationThread(); }, onFx -> {
                    check(!onFx, "database callable runs off the FX thread");
                    check(Platform.isFxApplicationThread() && !s.refresh.isDisabled(), "success runs on FX thread and re-enables controls");
                    s.status.setText("Loaded first result");
                    s.tasks.run("Refreshing timetable...", () -> 42, value -> {
                        check(value == 42 && !s.refresh.isDisabled(), "success callback can start a follow-up refresh");
                        s.status.setText("Loaded refreshed result"); done.countDown();
                    }, uncaught::add);
                }, uncaught::add);
                check(s.refresh.isDisabled(), "loading disables bound controls");
                s.tasks.run("Duplicate", duplicate::incrementAndGet, value -> duplicate.incrementAndGet(), uncaught::add);
                return null;
            });
            release.countDown(); await(done, "follow-up refresh completes");
            fx(() -> {
                check(duplicate.get() == 0, "duplicate request is not launched while busy");
                check(s.status.getText().equals("Loaded refreshed result"), "latest success updates controls");
                check(SessionManager.isCurrentSession(s.identity), "successful requests preserve the session");
                return null;
            });
        } finally { release.countDown(); fx(() -> { s.tasks.dispose(); return null; }); }
    }

    private static void queuedTimeoutAfterNavigation() throws Exception {
        var s = screen(); var release = new CountDownLatch(1); var callbacks = new AtomicInteger();
        try {
            fx(() -> {
                s.tasks.run("Loading timetable...", () -> { release.await(); return 1; },
                        value -> callbacks.incrementAndGet(), error -> callbacks.incrementAndGet());
                // Queue timeout delivery, then leave before its UI callback runs.
                ((PauseTransition) field(s.tasks, "timeout")).getOnFinished().handle(new javafx.event.ActionEvent());
                stage.getScene().setRoot(new BorderPane());
                return null;
            });
            fx(() -> {
                check(callbacks.get() == 0, "queued timeout cannot show an error after navigation");
                check(!s.refresh.isDisabled() && s.clears.get() == 1, "queued timeout cannot undo screen cleanup");
                return null;
            });
        } finally { release.countDown(); fx(() -> { s.tasks.dispose(); return null; }); }
    }

    private static void failureAndRecovery() throws Exception {
        for (boolean failInSuccess : List.of(false, true)) {
            var s = screen(); var done = new CountDownLatch(1);
            var rootCause = new SQLException("fixture SQL root cause");
            try {
                fx(() -> {
                    s.tasks.run("Loading timetable...", () -> {
                        if (!failInSuccess) throw new IllegalStateException("wrapper", rootCause);
                        return 1;
                    }, value -> { throw new IllegalStateException("render wrapper", rootCause); }, error -> {
                        check(Platform.isFxApplicationThread() && !s.refresh.isDisabled(), "failure runs on FX thread and releases controls");
                        check(TimetableTaskRunner.errorMessage("Load failed.", error).contains("fixture SQL root cause"), "failure retains the real cause");
                        s.status.setText("Load failed. Refresh to retry.");
                        s.tasks.run("Retrying...", () -> 2, value -> {
                            s.status.setText("Retry succeeded"); done.countDown();
                        }, uncaught::add);
                    });
                    return null;
                });
                await(done, "retry after failure completes");
                fx(() -> { check(!s.refresh.isDisabled() && s.status.getText().equals("Retry succeeded"), "failure leaves the screen usable"); return null; });
            } finally { fx(() -> { s.tasks.dispose(); return null; }); }
        }
    }

    private static void cancelAndRetry() throws Exception {
        var s = screen(); var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var retried = new CountDownLatch(1); var worker = new AtomicReference<Thread>(); var stale = new AtomicInteger();
        try {
            fx(() -> {
                s.tasks.run("Loading old timetable...", () -> {
                    worker.set(Thread.currentThread()); started.countDown(); awaitIgnoringInterrupt(release); return "old result";
                }, value -> stale.incrementAndGet(), error -> stale.incrementAndGet());
                return null;
            });
            await(started, "cancellable work starts");
            fx(() -> {
                ((Task<?>) field(s.tasks, "active")).cancel(true);
                check(!s.refresh.isDisabled() && s.status.getText().contains("cancelled"), "cancellation releases controls and ends loading");
                s.tasks.run("Loading new timetable...", () -> "new result", value -> {
                    s.status.setText(value); retried.countDown();
                }, uncaught::add);
                return null;
            });
            await(retried, "request after cancellation completes");
            release.countDown(); worker.get().join(2000);
            fx(() -> {
                check(stale.get() == 0 && s.status.getText().equals("new result"), "late cancelled result cannot overwrite a newer request");
                check(SessionManager.isCurrentSession(s.identity), "cancellation preserves the session");
                return null;
            });
        } finally { release.countDown(); fx(() -> { s.tasks.dispose(); return null; }); }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) try { latch.await(); break; } catch (InterruptedException ignored) { interrupted = true; }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static Object field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(CountDownLatch latch, String message) throws Exception { check(latch.await(5, TimeUnit.SECONDS), message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks.incrementAndGet(); }
}
