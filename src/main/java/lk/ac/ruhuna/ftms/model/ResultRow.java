package lk.ac.ruhuna.ftms.model;

/** Enrollment display data, including enrollments that have no saved course result. */
public record ResultRow(int enrollmentId, String registrationNo, String studentName,
                        int attemptNo, CourseResult result) {
    public ResultRow withResult(CourseResult replacement) {
        return new ResultRow(enrollmentId, registrationNo, studentName, attemptNo, replacement);
    }

    @Override
    public String toString() {
        return registrationNo + " - " + studentName
                + " (Enrollment " + enrollmentId + ", Attempt " + attemptNo + ")";
    }
}
