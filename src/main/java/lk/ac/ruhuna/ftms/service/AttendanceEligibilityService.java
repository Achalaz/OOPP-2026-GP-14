
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceEligibilityDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceEligibilityDAO.AttendanceCounts;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public class AttendanceEligibilityService
        implements AttendanceEligibilityProvider {

    private final AttendanceEligibilityDAO attendanceDAO;
    private final Double confirmedMinimumPercentage;

    // Default constructor:
    // Does not assume an unconfirmed attendance cutoff.
    public AttendanceEligibilityService() {

        this(new AttendanceEligibilityDAO(), null);
    }

    // Use this constructor only when the official
    // minimum attendance percentage is confirmed.
    public AttendanceEligibilityService(
            double confirmedMinimumPercentage) {

        this(
                new AttendanceEligibilityDAO(),
                confirmedMinimumPercentage
        );
    }

    // Constructor for dependency injection
    public AttendanceEligibilityService(
            AttendanceEligibilityDAO attendanceDAO,
            Double confirmedMinimumPercentage) {

        this.attendanceDAO = Objects.requireNonNull(
                attendanceDAO,
                "AttendanceEligibilityDAO cannot be null."
        );

        if (confirmedMinimumPercentage != null &&
                (!Double.isFinite(confirmedMinimumPercentage)
                        || confirmedMinimumPercentage < 0
                        || confirmedMinimumPercentage > 100)) {

            throw new IllegalArgumentException(
                    "Attendance minimum must be between 0 and 100."
            );
        }

        this.confirmedMinimumPercentage =
                confirmedMinimumPercentage;
    }

    // Get attendance details for one enrollment
    public AttendanceCounts getAttendanceCounts(
            int enrollmentId) throws SQLException {

        if (enrollmentId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid enrollment ID."
            );
        }

        return attendanceDAO.findByEnrollmentId(
                enrollmentId
        );
    }

    // Get raw combined attendance percentage
    public OptionalDouble getCombinedPercentage(
            int enrollmentId) throws SQLException {

        AttendanceCounts counts =
                getAttendanceCounts(enrollmentId);

        if (counts == null) {
            return OptionalDouble.empty();
        }

        Double percentage =
                counts.getCombinedPercentage();

        if (percentage == null) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(percentage);
    }

    // Implement AttendanceEligibilityProvider
    @Override
    public Optional<AttendanceEligibility> findByEnrollmentId(
            int enrollmentId) throws SQLException {
        return decide(getAttendanceCounts(enrollmentId));
    }

    /** Service integration must read attendance in the same academic transaction as the CA inputs. */
    public Optional<AttendanceEligibility> findByEnrollmentId(java.sql.Connection connection, int enrollmentId)
            throws SQLException {
        return decide(attendanceDAO.findByEnrollmentId(connection, enrollmentId));
    }

    private Optional<AttendanceEligibility> decide(AttendanceCounts counts) {

        // No enrollment, no official threshold, or
        // unresolved attendance statuses:
        // do not make a final eligibility decision.
        if (counts == null
                || confirmedMinimumPercentage == null
                || counts.requiresPolicyDecision()) {

            return Optional.empty();
        }

        Double percentage =
                counts.getCombinedPercentage();

        if (percentage == null) {
            return Optional.empty();
        }

        boolean eligible =
                percentage >= confirmedMinimumPercentage;

        return Optional.of(
                new AttendanceEligibility(
                        percentage,
                        eligible
                )
        );
    }
}
