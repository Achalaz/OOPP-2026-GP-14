package lk.ac.ruhuna.ftms.model;

/** Nullable values represent eligibility information that is not yet available. */
public record


EligibilityRow(
        int enrollmentId,
        Double caPercentage,
        Boolean caEligible,
        Double attendancePercentage,
        Boolean attendanceEligible,
        Boolean overallEligible,
        String reason,
        boolean saved) {

    public static EligibilityRow fromResult(EligibilityResult result, boolean saved) {
        return new EligibilityRow(
                result.getEnrollmentId(), result.getCaPercentage(), result.isCaEligible(),
                result.getAttendancePercentage(), result.isAttendanceEligible(),
                result.isOverallEligible(), result.getReason(), saved
        );
    }

    @Override
    public String toString() {
        return "Enrollment " + enrollmentId;
    }
}
