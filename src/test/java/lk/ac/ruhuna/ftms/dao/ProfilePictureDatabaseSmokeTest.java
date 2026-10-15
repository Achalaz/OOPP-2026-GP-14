package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.service.ProfileService;
import lk.ac.ruhuna.ftms.util.ProfilePictureStore;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Existing-schema MySQL checks, with rollback and a temporary picture directory. No DDL. */
public final class ProfilePictureDatabaseSmokeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--rollback-write")) {
            throw new IllegalArgumentException("Run explicitly with --rollback-write.");
        }
        Path testRoot = Files.createTempDirectory(Path.of("target"), "profile-picture-db-");
        try {
            Path managed = testRoot.resolve("managed");
            ProfilePictureStore store = new ProfilePictureStore(managed);
            Path png = testRoot.resolve("source.png");
            Path jpg = testRoot.resolve("source.jpg");
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", png.toFile());
            ImageIO.write(image, "jpg", jpg.toFile());
            try (Connection connection = DBConnection.getConnection()) {
                var original = ProfileDatabaseSmokeTest.snapshot(connection);
                connection.setAutoCommit(false);
                try {
                    Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                                if (method.getName().equals("close")) { return null; }
                                try { return method.invoke(connection, arguments); }
                                catch (InvocationTargetException error) { throw error.getCause(); }
                            });
                    ProfileDAO dao = new ProfileDAO() {
                        @Override protected Connection openConnection() { return borrowed; }
                    };
                    var accounts = ProfileDatabaseSmokeTest.accounts(connection);
                    for (String role : new String[]{"ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"}) {
                        connection.rollback();
                        var user = accounts.stream().filter(account -> role.equals(account.getRole())).findFirst().orElseThrow();
                        SessionManager.startSession(user);
                        ProfileService service = new ProfileService(dao, store);
                        String first = service.changeMyProfilePicture(png).getProfilePicture();
                        check(first.equals(service.getMyProfile().getProfilePicture()), "picture filename round trip");
                        check(Files.exists(managed.resolve(first)), "managed PNG exists");
                        check(service.loadMyProfilePicture(first) != null, "managed picture loads");
                        var changed = ProfileDatabaseSmokeTest.snapshot(connection);
                        for (var row : original.entrySet()) {
                            for (var column : row.getValue().entrySet()) {
                                if (row.getKey() == user.getUserId()
                                        && List.of("profile_picture", "updated_at").contains(column.getKey())) { continue; }
                                check(java.util.Objects.equals(column.getValue(), changed.get(row.getKey()).get(column.getKey())),
                                        "picture update preserves other columns and other users");
                            }
                        }
                        String second = service.changeMyProfilePicture(jpg).getProfilePicture();
                        check(!Files.exists(managed.resolve(first)), "replacement deletes old file");
                        check(Files.exists(managed.resolve(second)), "replacement file retained");
                        check(!dao.updateProfilePicture(SessionManager.requireSession(), first, first), "stale comparison rejects update");
                        check(second.equals(service.getMyProfile().getProfilePicture()), "stale request leaves new picture intact");
                        service.removeMyProfilePicture();
                        check(service.getMyProfile().getProfilePicture() == null, "remove stores SQL NULL");
                        check(!Files.exists(managed.resolve(second)), "remove deletes managed file");
                        String missing = "u" + user.getUserId() + "-" + UUID.randomUUID() + ".png";
                        check(dao.updateProfilePicture(SessionManager.requireSession(), null, missing), "missing-file fixture stored in transaction");
                        check(service.loadMyProfilePicture(missing) == null, "missing file yields default avatar data");
                        service.removeMyProfilePicture();
                        try (var statement = connection.prepareStatement("UPDATE users SET is_active = FALSE WHERE user_id = ?")) {
                            statement.setInt(1, user.getUserId());
                            statement.executeUpdate();
                        }
                        check(!dao.updateProfilePicture(SessionManager.requireSession(), null, missing), "inactive account picture update rejected");
                    }
                } finally {
                    connection.rollback();
                    SessionManager.logout();
                }
                try (Connection verification = DBConnection.getConnection()) {
                    check(original.equals(ProfileDatabaseSmokeTest.snapshot(verification)), "every user row unchanged after rollback");
                }
            }
            System.out.println("Profile picture MySQL checks passed: " + checks + "; four roles; all writes rolled back; no DDL.");
        } finally {
            SessionManager.logout();
            try (var files = Files.walk(testRoot)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
}
