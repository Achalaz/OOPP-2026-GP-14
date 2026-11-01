package lk.ac.ruhuna.ftms.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableEntry;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.service.TimetableDatabaseFixture;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Explicit JavaFX acceptance runner; live navigation only reads, fixture writes use temporary tables. */
public final class LecturerTimetableViewSmokeTest {
    private static Stage stage;
    private static Parent root;
    private static int checks;

    public static void main(String[] args) throws Exception {
        String before = TimetableDatabaseFixture.productionFingerprint();
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timeout");
        Platform.setImplicitExit(false);
        try {
            var lecturer = new UserDAO().getAllUsers().stream()
                    .filter(u -> u.isActive() && "LECTURER".equals(u.getRole())).findFirst().orElseThrow();
            fx(() -> {
                stage = new Stage();
                stage.setOpacity(0);
                stage.setScene(new Scene(new BorderPane(), 1180, 740));
                stage.show();
                SessionManager.startSession(lecturer);
                return null;
            });
            for (String button : List.of("timetableNav", "timetableCard")) {
                fx(() -> {
                    root = new FXMLLoader(LecturerTimetableViewSmokeTest.class.getResource("/fxml/lecturer_dashboard.fxml")).load();
                    stage.getScene().setRoot(root);
                    root.applyCss(); root.layout();
                    double width = stage.getWidth(), height = stage.getHeight();
                    ((Button) root.lookup("#" + button)).fire();
                    root = stage.getScene().getRoot();
                    check("lecturerTimetableRoot".equals(root.getId()), button + " opens My Timetable");
                    check(Math.abs(width - stage.getWidth()) < 2 && Math.abs(height - stage.getHeight()) < 2,
                            "Navigation preserves window size");
                    return null;
                });
                awaitLoaded();
                fx(() -> {
                    check(table().getItems().stream().allMatch(e -> Integer.valueOf(lecturer.getUserId()).equals(e.lecturerId())),
                            "Live reads contain only the signed-in lecturer's entries");
                    back();
                    return null;
                });
            }

            try (var db = new TimetableDatabaseFixture()) {
                fx(() -> {
                    TimetableDatabaseFixture.login(4, "LECTURER");
                    var loader = new FXMLLoader(LecturerTimetableViewSmokeTest.class.getResource("/fxml/lecturer_timetable.fxml"));
                    loader.setControllerFactory(type -> new LecturerTimetableController(db.lecturer()));
                    root = loader.load();
                    stage.getScene().setRoot(root);
                    return null;
                });
                awaitLoaded();
                fx(() -> {
                    check(table().getItems().isEmpty(), "Empty fixture displays no entries");
                    check(status().contains("no assigned timetable entries"), "Clear empty timetable status");
                    check(((Label) table().getPlaceholder()).getText().contains("no assigned timetable entries"), "Clear table placeholder");
                    check(!table().isEditable(), "Read-only table");
                    check(root.lookupAll(".text-field").isEmpty(), "No user ID entry field");
                    check(table().getColumns().stream().map(c -> c.getText()).toList().equals(
                            List.of("Day", "Start Time", "End Time", "Course", "Group", "Session Type", "Room", "Effective Dates")),
                            "All requested columns displayed");
                    return null;
                });
                db.execute("""
                        INSERT INTO timetable_entries(offering_id,lecturer_id,day_of_week,start_time,end_time,room,session_type,effective_from,effective_to)
                        VALUES(10,4,'MONDAY','08:30','09:30','LAB-1','PRACTICAL','2026-01-01','2026-06-30'),
                              (10,5,'TUESDAY','10:00','11:00','LT-1','LECTURE',NULL,NULL)
                        """);
                fx(() -> { ((Button) root.lookup("#refreshButton")).fire(); return null; });
                awaitLoaded();
                fx(() -> {
                    check(table().getItems().size() == 1, "Refresh loads only lecturer's assigned row");
                    var entry = table().getItems().getFirst();
                    check("08:30".equals(table().getColumns().get(1).getCellData(entry)), "Start time displayed");
                    check("09:30".equals(table().getColumns().get(2).getCellData(entry)), "End time displayed");
                    check("2026-01-01 to 2026-06-30".equals(table().getColumns().get(7).getCellData(entry)), "Effective dates displayed");
                    snapshot();
                    return null;
                });
                for (int[] size : List.of(new int[]{1000, 650}, new int[]{760, 540}, new int[]{640, 480})) {
                    fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
                    Thread.sleep(150);
                    fx(() -> {
                        root.applyCss(); root.layout();
                        Button button = (Button) root.lookup("#backButton");
                        var bounds = button.localToScene(button.getBoundsInLocal());
                        check(bounds.getMinX() >= 0 && bounds.getMaxX() <= stage.getScene().getWidth() + 1,
                                "Back button fits at width " + size[0]);
                        return null;
                    });
                }
                fx(() -> { back(); return null; });
            }
            check(before.equals(TimetableDatabaseFixture.productionFingerprint()), "Production contents unchanged");
            System.out.println("Lecturer timetable JavaFX: " + checks + " checks passed; production data unchanged.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); SessionManager.logout(); return null; });
            Platform.exit();
        }
    }

    private static void back() {
        var identity = SessionManager.requireSession();
        ((Button) root.lookup("#backButton")).fire();
        root = stage.getScene().getRoot();
        check("lecturerRoot".equals(root.getId()), "Back returns to Lecturer Dashboard");
        check(SessionManager.isCurrentSession(identity), "Back preserves login");
    }

    private static void awaitLoaded() throws Exception {
        fx(() -> { root.applyCss(); root.layout(); return null; });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        while (System.nanoTime() < deadline) {
            if (fx(() -> !((Button) root.lookup("#refreshButton")).isDisabled()
                    && (status().startsWith("Showing ") || status().contains("no assigned timetable entries")))) return;
            Thread.sleep(30);
        }
        throw new AssertionError("Timetable did not load: " + fx(LecturerTimetableViewSmokeTest::status));
    }

    private static void snapshot() throws Exception {
        root.applyCss(); root.layout();
        var image = root.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var output = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        var directory = java.nio.file.Path.of("target", "timetable-verification");
        java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve("lecturer-timetable.png").toFile());
    }

    @SuppressWarnings("unchecked")
    private static TableView<TimetableEntry> table() { return (TableView<TimetableEntry>) root.lookup("#timetableTable"); }
    private static String status() { return ((Label) root.lookup("#statusLabel")).getText(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(40, TimeUnit.SECONDS);
    }
}
