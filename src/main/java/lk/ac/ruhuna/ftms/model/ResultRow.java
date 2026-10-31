package lk.ac.ruhuna.ftms.model;

/** Enrollment display data, including enrollments that have no saved course result. */
public record ResultRow(int enrollmentId, String registrationNo, String studentName,
                        int attemptNo, CourseResult result, Boolean overallEligible, String eligibilityReason) {
    public ResultRow(int enrollmentId, String registrationNo, String studentName, int attemptNo, CourseResult result) {
        this(enrollmentId, registrationNo, studentName, attemptNo, result, null, "Eligibility not loaded.");
    }
    public ResultRow withResult(CourseResult replacement) {
        return new ResultRow(enrollmentId, registrationNo, studentName, attemptNo, replacement, overallEligible, eligibilityReason);
    }

    @Override
    public String toString() {
        return registrationNo + " - " + studentName
                + " (Enrollment " + enrollmentId + ", Attempt " + attemptNo + ")";
    }
}
