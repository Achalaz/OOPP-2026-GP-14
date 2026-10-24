package lk.ac.ruhuna.ftms.service;

import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

/** Shared managed storage only; role authorization belongs to the calling service. */
final class MaterialStorage {
    static final long LIMIT = 20L * 1024 * 1024;
    private final Path root;

    static Path configuredRoot() {
        return Path.of(System.getProperty("ftms.materials.dir",
                Path.of(System.getProperty("user.home"), ".ftms", "course-materials").toString()));
    }

    MaterialStorage(Path root) { this.root = Objects.requireNonNull(root).toAbsolutePath().normalize(); }
    private MaterialStorage() { this.root = null; }

    static MaterialStorage configured() {
        try { return new MaterialStorage(configuredRoot()); }
        catch (InvalidPathException | SecurityException error) { return new MaterialStorage(); }
    }

    String store(Path source) throws IOException {
        checkCancelled();
        if (root == null) throw new IOException("Material storage is unavailable. Check its configuration.");
        if (source.getFileName() == null) throw new IllegalArgumentException("Choose a regular file.");
        String original = source.getFileName().toString();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot).toLowerCase(Locale.ROOT);
        if (!Set.of(".pdf", ".txt", ".png", ".jpg", ".jpeg", ".pptx", ".docx", ".xlsx", ".zip").contains(extension))
            throw new IllegalArgumentException("Supported files: PDF, TXT, images, Office documents and ZIP.");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > LIMIT)
            throw new IllegalArgumentException("Choose a regular file up to 20 MiB.");
        byte[] bytes;
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes((int) LIMIT + 1); }
        if (bytes.length == 0 || bytes.length > LIMIT)
            throw new IllegalArgumentException("File must be nonempty and at most 20 MiB.");
        Files.createDirectories(root);
        checkRoot();
        checkCancelled();
        String name = UUID.randomUUID() + extension;
        Files.write(root.resolve(name), bytes, LinkOption.NOFOLLOW_LINKS, StandardOpenOption.CREATE_NEW);
        checkRoot();
        return name;
    }

    static boolean managedName(String name) {
        return name != null && name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]+");
    }

    byte[] read(String name) throws IOException {
        checkCancelled();
        if (!managedName(name)) throw new IOException("File unavailable. Contact your lecturer.");
        checkRoot();
        Path file = root.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > LIMIT)
            throw new IOException("File unavailable. Contact your lecturer.");
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes((int) LIMIT + 1); }
        checkRoot();
        checkCancelled();
        if (bytes.length == 0 || bytes.length > LIMIT) throw new IOException("File unavailable. Contact your lecturer.");
        return bytes;
    }

    String availability(String name) {
        if (!managedName(name)) return "Legacy/external metadata - uploader must upload file";
        try {
            checkRoot();
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file)
                    || Files.size(file) == 0 || Files.size(file) > LIMIT) return "File unavailable";
            return "Available";
        } catch (IOException | SecurityException error) { return "Storage unavailable"; }
    }

    @FunctionalInterface interface Authorization { void check() throws SQLException; }

    /** Writes a sibling temporary file first, preserving an existing destination if authorization or IO fails. */
    void export(byte[] bytes, Path destination, Authorization authorization) throws IOException, SQLException {
        authorization.check();
        checkCancelled();
        Path target = Objects.requireNonNull(destination).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !parent.toRealPath().equals(parent) || Files.isSymbolicLink(target))
            throw new IOException("Choose a destination without redirected directories or symbolic links.");
        if (target.startsWith(root)) throw new IOException("Choose a destination outside managed material storage.");
        Path staged = Files.createTempFile(parent, ".ftms-export-", ".tmp");
        try {
            Files.write(staged, bytes, LinkOption.NOFOLLOW_LINKS, StandardOpenOption.TRUNCATE_EXISTING);
            authorization.check();
            checkCancelled();
            if (!parent.toRealPath().equals(parent) || Files.isSymbolicLink(target))
                throw new IOException("The destination changed. Choose another location.");
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staged); }
    }

    private void checkRoot() throws IOException {
        if (root == null) throw new IOException("Material storage is unavailable.");
        if (!root.toRealPath().equals(root)) throw new IOException("Material storage must not use redirected directories.");
    }

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Operation cancelled.");
    }
}
