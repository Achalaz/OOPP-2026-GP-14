package lk.ac.ruhuna.ftms.model;

/** Read-only undergraduate choice scoped to a course offering. */
public record AttendanceEnrollment(int enrollmentId, String registrationNo, String studentName) {
    @Override public String toString() {
        return registrationNo + " - " + studentName + " (#" + enrollmentId + ")";
    }
}
