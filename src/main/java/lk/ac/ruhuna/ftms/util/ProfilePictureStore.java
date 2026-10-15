package lk.ac.ruhuna.ftms.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Managed local files only; callers supply the authenticated owner, never a directory from the UI. */
public final class ProfilePictureStore {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private static final Pattern FILENAME = Pattern.compile(
            "u[1-9][0-9]*-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)");
    private final Path directory;

    public ProfilePictureStore() {
        this(Path.of(System.getProperty("user.home"), ".ftms", "profile-pictures"));
    }

    /** Also permits an isolated temporary directory in tests. */
    public ProfilePictureStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public String save(int ownerId, Path source) throws IOException {
        if (ownerId <= 0 || source == null) { throw new IllegalArgumentException("Choose a JPG or PNG picture."); }
        String extension = extension(source.getFileName().toString());
        byte[] bytes = readBounded(source);
        validateImage(bytes, extension);
        ensureDirectory(true);
        String filename = "u" + ownerId + "-" + UUID.randomUUID() + "." + extension;
        Path destination = resolve(ownerId, filename);
        boolean created = false;
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            created = true;
            output.write(bytes);
        } catch (IOException error) {
            if (created) {
                try { Files.deleteIfExists(destination); }
                catch (IOException cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
        return filename;
    }

    public byte[] read(int ownerId, String filename) throws IOException {
        if (filename == null) { return null; }
        Path path = resolve(ownerId, filename);
        if (!ensureDirectory(false) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { return null; }
        byte[] bytes = readBounded(path);
        validateImage(bytes, extension(filename));
        return bytes;
    }

    /** Never deletes outside this owner's generated filename namespace, including legacy DB paths. */
    public void delete(int ownerId, String filename) throws IOException {
        if (filename == null || !isManagedFilename(ownerId, filename)) { return; }
        Path path = resolve(ownerId, filename);
        if (ensureDirectory(false)) {
            // Delete the link itself if a local file was replaced by a symlink; never follow it.
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The managed picture path is not a file.");
            }
            Files.deleteIfExists(path);
        }
    }

    public static boolean isManagedFilename(int ownerId, String filename) {
        return ownerId > 0 && filename != null && filename.startsWith("u" + ownerId + "-")
                && FILENAME.matcher(filename).matches();
    }

    private Path resolve(int ownerId, String filename) {
        if (!isManagedFilename(ownerId, filename)) {
            throw new IllegalArgumentException("The saved picture reference is invalid. Choose a new picture.");
        }
        Path resolved = directory.resolve(filename).normalize();
        if (!resolved.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid picture path.");
        }
        return resolved;
    }

    private boolean ensureDirectory(boolean create) throws IOException {
        rejectLinkedDirectories();
        if (create) { Files.createDirectories(directory); }
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) { return false; }
        rejectLinkedDirectories();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory)) {
            throw new IOException("The profile picture directory must be a regular local directory.");
        }
        return true;
    }

    private void rejectLinkedDirectories() throws IOException {
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("The profile picture directory cannot contain symbolic links.");
            }
        }
    }

    private static String extension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) { return "png"; }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) { return "jpg"; }
        throw new IllegalArgumentException("Choose a JPG or PNG picture.");
    }

    private static byte[] readBounded(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Choose an accessible regular JPG or PNG file.");
        }
        if (Files.size(path) > MAX_BYTES) { throw new IllegalArgumentException("Pictures must be 2 MB or smaller."); }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            // A bounded read also handles a file growing after the size check.
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) { throw new IllegalArgumentException("Pictures must be 2 MB or smaller."); }
            return bytes;
        }
    }

    private static void validateImage(byte[] bytes, String extension) throws IOException {
        // Decoders may tolerate truncation or trailing payloads; require the normal file terminator too.
        byte[] end = extension.equals("png")
                ? new byte[]{0, 0, 0, 0, 73, 69, 78, 68, (byte) 174, 66, 96, (byte) 130}
                : new byte[]{(byte) 255, (byte) 217};
        if (bytes.length < end.length) { throw invalidImage(); }
        for (int index = 0; index < end.length; index++) {
            if (bytes[bytes.length - end.length + index] != end[index]) { throw invalidImage(); }
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw invalidImage(); }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") && extension.equals("png"))
                        && !(format.equals("jpeg") && extension.equals("jpg"))) {
                    throw invalidImage();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Picture dimensions must be at most 8192 pixels per side and 16 megapixels.");
                }
                boolean[] damaged = {false};
                reader.addIIOReadWarningListener((ignored, warning) -> damaged[0] = true);
                if (reader.read(0) == null || damaged[0]) { throw invalidImage(); }
            } catch (javax.imageio.IIOException error) {
                throw invalidImage();
            } finally {
                reader.dispose();
            }
        }
    }

    private static IllegalArgumentException invalidImage() {
        return new IllegalArgumentException("The file must be a complete, valid JPG or PNG matching its file extension.");
    }
}
