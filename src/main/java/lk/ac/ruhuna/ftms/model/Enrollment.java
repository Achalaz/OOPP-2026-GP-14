package lk.ac.ruhuna.ftms.model;

/** Enrollment identity and joined display values. Academic child records are never replaced. */
public record Enrollment(int enrollmentId, int studentId, String registrationNo, String username,
                         String studentName, int offeringId, String courseCode, String courseName,
                         String semester, String academicYear, String offeringGroup, String offeringStatus,
                         int attempt, String status, java.time.LocalDateTime enrolledAt) {}
