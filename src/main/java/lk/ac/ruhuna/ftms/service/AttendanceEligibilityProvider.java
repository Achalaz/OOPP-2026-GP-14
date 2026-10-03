package lk.ac.ruhuna.ftms.service;

import java.util.Optional;

/**
 * Integration point for the Attendance module's official percentage and decision.
 * No attendance threshold or medical policy is defined by the eligibility UI.
 * Implementations are called on a background thread and must not access JavaFX controls.
 */
@FunctionalInterface
public interface AttendanceEligibilityProvider {

    /** Empty means the Attendance module cannot yet supply a decision. */
    Optional<AttendanceEligibility> findByEnrollmentId(int enrollmentId) throws Exception;

    record AttendanceEligibility(double percentage, boolean eligible) {
        public AttendanceEligibility {
            if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("Attendance percentage must be between 0 and 100.");
            }
        }
    }
}
