
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseOfferingDAO;
import lk.ac.ruhuna.ftms.model.CourseOffering;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class CourseOfferingService {

    private final CourseOfferingDAO offeringDAO;
    private final BooleanSupplier isAdmin;

    public CourseOfferingService(
            CourseOfferingDAO offeringDAO,
            BooleanSupplier isAdmin) {

        this.offeringDAO =
                Objects.requireNonNull(offeringDAO);
        this.isAdmin =
                Objects.requireNonNull(isAdmin);
    }

    private void requireAdmin() {
        if (!isAdmin.getAsBoolean()) {
            throw new SecurityException(
                    "Only Admin users can manage course offerings."
            );
        }
    }

    public List<CourseOffering> getAllOfferings()
            throws SQLException {

        requireAdmin();
        return offeringDAO.getAllOfferings();
    }

    public List<CourseOffering> searchOfferings(String keyword)
            throws SQLException {

        requireAdmin();

        if (keyword == null || keyword.isBlank()) {
            return offeringDAO.getAllOfferings();
        }

        return offeringDAO.searchOfferings(keyword.trim());
    }

    public boolean saveOffering(CourseOffering offering)
            throws SQLException {

        requireAdmin();

        if (offering == null) {
            throw new IllegalArgumentException(
                    "Please provide course offering details."
            );
        }

        return offeringDAO.saveOffering(offering);
    }

    public boolean deleteOffering(int offeringId)
            throws SQLException {

        requireAdmin();

        if (offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        return offeringDAO.deleteOffering(offeringId);
    }

    public List<CourseOfferingDAO.CourseOption> getActiveCourses()
            throws SQLException {

        requireAdmin();
        return offeringDAO.getActiveCourses();
    }

    public List<CourseOfferingDAO.SemesterOption> getSemesters()
            throws SQLException {

        requireAdmin();
        return offeringDAO.getSemesters();
    }
}
