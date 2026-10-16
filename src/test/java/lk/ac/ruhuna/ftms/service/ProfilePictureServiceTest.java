package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.ProfileDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.ProfilePictureStore;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/** Real image/file tests with an in-memory database. Never writes to the normal application directory. */
public final class ProfilePictureServiceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path testRoot = Files.createTempDirectory(Path.of("target"), "profile-picture-tests-").toAbsolutePath();
        try {
            Path directory = testRoot.resolve("managed");
            ProfilePictureStore store = new ProfilePictureStore(directory);
            Path png = testRoot.resolve("source.PNG");
            Path jpg = testRoot.resolve("source.jpeg");
            BufferedImage image = new BufferedImage(24, 18, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xff234f80);
            check(ImageIO.write(image, "png", png.toFile()), "PNG fixture written");
            check(ImageIO.write(image, "jpg", jpg.toFile()), "JPEG fixture written");
            byte[] sourceBytes = Files.readAllBytes(png);
            for (String role : new String[]{"ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"}) {
                MemoryDAO dao = new MemoryDAO(role);
                SessionManager.startSession(dao.copy());
                ProfileService service = new ProfileService(dao, store);
                User changed = service.changeMyProfilePicture(png);
                String first = changed.getProfilePicture();
                check(ProfilePictureStore.isManagedFilename(7, first), "generated owner filename for " + role);
                check(!first.contains("source"), "source filename is not persisted");
                check(Arrays.equals(sourceBytes, Files.readAllBytes(directory.resolve(first))), "approved bytes copied");
                check(Arrays.equals(sourceBytes, service.loadMyProfilePicture(first)), "picture reloads");
                check(first.equals(SessionManager.getCurrentUser().getProfilePicture()), "session picture refreshed");
                check(changed.getUsername().equals("owner") && changed.getRole().equals(role), "identity preserved");
                check(changed.getFullName().equals("Original Name"), "picture update preserves full name");
                changed = service.changeMyProfilePicture(jpg);
                String second = changed.getProfilePicture();
                check(second.endsWith(".jpg"), "JPEG has canonical extension");
                check(!Files.exists(directory.resolve(first)), "replacement removes obsolete picture");
                check(Files.exists(directory.resolve(second)), "replacement retained");
                service.removeMyProfilePicture();
                check(dao.picture == null, "remove stores SQL-null equivalent");
                check(SessionManager.getCurrentUser().getProfilePicture() == null, "remove refreshes session");
                check(!Files.exists(directory.resolve(second)), "remove deletes old file");
                check(service.loadMyProfilePicture(null) == null, "null selects default avatar");
                service.removeMyProfilePicture();
                check(Arrays.equals(sourceBytes, Files.readAllBytes(png)), "user source file is never changed");
            }

            MemoryDAO dao = new MemoryDAO("LECTURER");
            SessionManager.startSession(dao.copy());
            ProfileService service = new ProfileService(dao, store);
            Path fake = testRoot.resolve("fake.png");
            Files.writeString(fake, "This is not an image");
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(fake));
            Path renamed = testRoot.resolve("renamed.jpg");
            Files.write(renamed, sourceBytes);
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(renamed));
            Path gif = testRoot.resolve("disguised.png");
            ImageIO.write(image, "gif", gif.toFile());
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(gif));
            Path oversized = testRoot.resolve("oversized.png");
            Files.write(oversized, new byte[ProfilePictureStore.MAX_BYTES + 1]);
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(oversized));
            Path truncated = testRoot.resolve("truncated.png");
            Files.write(truncated, Arrays.copyOf(sourceBytes, sourceBytes.length - 12));
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(truncated));
            Path huge = testRoot.resolve("dimensions.png");
            ImageIO.write(new BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB), "png", huge.toFile());
            expect(IllegalArgumentException.class, () -> service.changeMyProfilePicture(huge));
            check(fileCount(directory) == 0, "rejected files leave no managed copies");

            String current = service.changeMyProfilePicture(png).getProfilePicture();
            for (String path : new String[]{"../source.PNG", "..\\source.PNG", png.toString(),
                    "https://example.com/photo.png", "u8-" + current.substring(3), "u7-../../source.PNG"}) {
                expect(IllegalArgumentException.class, () -> service.loadMyProfilePicture(path));
                store.delete(7, path);
            }
            check(Files.exists(png), "unsafe stored paths are never deleted");
            expect(IllegalArgumentException.class, () -> new ProfileDAO().updateProfilePicture(
                    SessionManager.requireSession(), current, "../other.png"));
            expect(SecurityException.class, () -> new ProfileDAO().updateProfilePicture(
                    new SessionIdentity(-1, 8), null, current));

            dao.conflict = true;
            expect(IllegalStateException.class, () -> service.changeMyProfilePicture(jpg));
            check(fileCount(directory) == 1 && Files.exists(directory.resolve(current)), "conflict removes abandoned upload only");
            expect(IllegalStateException.class, service::removeMyProfilePicture);
            check(Files.exists(directory.resolve(current)), "failed removal retains file");
            dao.conflict = false;
            dao.failBeforeSave = true;
            expect(SQLException.class, () -> service.changeMyProfilePicture(jpg));
            check(fileCount(directory) == 1, "confirmed SQL failure removes new orphan");
            dao.failBeforeSave = false;

            dao.failAfterSave = true;
            expect(SQLException.class, () -> service.changeMyProfilePicture(jpg));
            check(Files.exists(directory.resolve(dao.picture)), "uncertain commit never deletes a referenced file");
            dao.failAfterSave = false;
            service.getMyProfile();
            check(dao.picture.equals(SessionManager.getCurrentUser().getProfilePicture()), "refresh resolves uncertain save");
            // The prior file is conservatively retained after an uncertain SQL result.
            store.delete(7, current);
            service.removeMyProfilePicture();
            check(fileCount(directory) == 0, "cleanup completes without touching user originals");

            dao.endSessionOnWrite = true;
            expect(SecurityException.class, () -> service.changeMyProfilePicture(png));
            check(fileCount(directory) == 0, "session change before SQL removes staged picture");
            expect(SecurityException.class, service::removeMyProfilePicture);
            check(SessionManager.getCurrentUserIdOrNull() == 8, "other login preserved");
            SessionManager.logout();
            expect(SecurityException.class, () -> service.changeMyProfilePicture(png));
            System.out.println("Profile picture/file/security checks passed: " + checks);
        } finally {
            SessionManager.logout();
            // Only the exact temporary tree created by this runner is removed.
            try (var files = Files.walk(testRoot)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
            }
        }
    }

    private static long fileCount(Path directory) throws Exception {
        try (var files = Files.list(directory)) { return files.count(); }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static void expect(Class<? extends Throwable> type, Work action) throws Exception {
        checks++;
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) { return; }
            throw new AssertionError("Expected " + type.getSimpleName(), error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private interface Work { void run() throws Exception; }

    private static final class MemoryDAO extends ProfileDAO {
        private final String role;
        private String picture;
        private boolean conflict;
        private boolean failBeforeSave;
        private boolean failAfterSave;
        private boolean endSessionOnWrite;
        MemoryDAO(String role) { this.role = role; }
        User copy() {
            User user = new User(7, "owner", "Original Name", "owner@example.com", role, true);
            user.setProfilePicture(picture);
            return user;
        }
        @Override public User getProfile(SessionIdentity identity) { return copy(); }
        @Override public boolean updateProfilePicture(SessionIdentity identity, String expected, String filename)
                throws SQLException {
            if (endSessionOnWrite) {
                SessionManager.startSession(new User(8, "other", "Other", "other@example.com", "STUDENT", true));
            }
            SessionManager.requireSession(identity);
            if (failBeforeSave) { throw new SQLException("Synthetic SQL failure", "08001"); }
            if (conflict || !Objects.equals(expected, picture)) { return false; }
            picture = filename;
            if (failAfterSave) { throw new SQLException("Synthetic lost acknowledgement", "08001"); }
            return true;
        }
    }
}
