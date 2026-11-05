package lk.ac.ruhuna.ftms.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import lk.ac.ruhuna.ftms.dao.CourseMaterialDAO;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO;
import lk.ac.ruhuna.ftms.model.CourseMaterial;

/** Read-only student API. Every operation revalidates the account, profile, enrollment and offering. */
public final class StudentCourseMaterialService {
    public static final String UNAVAILABLE = "File unavailable. Contact your lecturer.";
    private final StudentAccessService access = new StudentAccessService();
    private final EnrollmentDAO enrollments = new EnrollmentDAO();
    private final CourseMaterialDAO materials = new CourseMaterialDAO();
    private final MaterialStorage storage = MaterialStorage.configured();

    public List<CourseMaterial> list(int offering) throws SQLException {
        return access.read((connection, student) -> {
            MaterialStorage.checkCancelled();
            enrollments.requireMaterialAccess(connection, student, offering);
            return materials.findByOffering(connection, offering).stream().map(m -> new CourseMaterial(
                    m.materialId(), m.offeringId(), m.title(), m.description(), m.materialType(),
                    MaterialStorage.managedName(m.resourcePath()) ? m.resourcePath() : null,
                    m.uploadedBy(), m.uploadedAt(), m.uploaderName(),
                    "Available".equals(storage.availability(m.resourcePath())) ? "Available" : UNAVAILABLE)).toList();
        });
    }

    public byte[] download(int offering, int materialId) throws SQLException, IOException {
        return read(offering, materialId, null);
    }

    public void export(int offering, int materialId, Path destination) throws SQLException, IOException {
        java.util.Objects.requireNonNull(destination, "Choose a destination.");
        read(offering, materialId, destination);
    }

    private byte[] read(int offering, int materialId, Path destination) throws SQLException, IOException {
        try {
            return access.read((connection, student) -> {
                MaterialStorage.checkCancelled();
                enrollments.requireMaterialAccess(connection, student, offering);
                CourseMaterial material = find(connection, offering, materialId);
                byte[] bytes;
                try { bytes = storage.read(material.resourcePath()); }
                catch (IOException | SecurityException error) { throw new UncheckedIOException(new IOException(UNAVAILABLE)); }
                access.requireSession();
                MaterialStorage.checkCancelled();
                if (destination != null) {
                    try {
                        // Account/enrollment/offering share locks remain held until the export finishes.
                        storage.export(bytes, destination, () -> {
                            access.requireSession();
                            MaterialStorage.checkCancelled();
                        });
                    } catch (IOException error) {
                        throw new UncheckedIOException(new IOException("Unable to save the file. Choose another destination and try again."));
                    }
                }
                return bytes;
            });
        } catch (UncheckedIOException error) { throw error.getCause(); }
    }

    private CourseMaterial find(Connection connection, int offering, int materialId) throws SQLException {
        return materials.findByOffering(connection, offering).stream().filter(m -> m.materialId() == materialId)
                .findFirst().orElseThrow(() -> new SecurityException("Material is not available in this course offering."));
    }

    public static String suggestedFileName(CourseMaterial material) {
        String name = material.resourcePath();
        return "material-" + material.materialId()
                + (MaterialStorage.managedName(name) ? name.substring(name.lastIndexOf('.')) : ".bin");
    }
}
