
package lk.ac.ruhuna.ftms.model;

public final class AttendanceSummary {

    private final int enrollmentId;
    private final String registrationNo;
    private final String studentName;

    private final int theorySessions;
    private final int theoryPresent;

    private final int practicalSessions;
    private final int practicalPresent;

    private final int lateCount;
    private final int excusedCount;
    private final int medicalCount;
    private final int unmarkedCount;

    public AttendanceSummary(
            int enrollmentId,
            String registrationNo,
            String studentName,
            int theorySessions,
            int theoryPresent,
            int practicalSessions,
            int practicalPresent,
            int lateCount,
            int excusedCount,
            int medicalCount,
            int unmarkedCount) {

        this.enrollmentId = enrollmentId;
        this.registrationNo = registrationNo;
        this.studentName = studentName;

        this.theorySessions = theorySessions;
        this.theoryPresent = theoryPresent;

        this.practicalSessions = practicalSessions;
        this.practicalPresent = practicalPresent;

        this.lateCount = lateCount;
        this.excusedCount = excusedCount;
        this.medicalCount = medicalCount;
        this.unmarkedCount = unmarkedCount;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public int getTheorySessions() {
        return theorySessions;
    }

    public int getTheoryPresent() {
        return theoryPresent;
    }

    public int getPracticalSessions() {
        return practicalSessions;
    }

    public int getPracticalPresent() {
        return practicalPresent;
    }

    public int getLateCount() {
        return lateCount;
    }

    public int getExcusedCount() {
        return excusedCount;
    }

    public int getMedicalCount() {
        return medicalCount;
    }

    public int getUnmarkedCount() {
        return unmarkedCount;
    }

    public int getTotalSessions() {
        return theorySessions + practicalSessions;
    }

    public int getTotalPresent() {
        return theoryPresent + practicalPresent;
    }

    public Double getTheoryPercentage() {
        return percentage(theoryPresent, theorySessions);
    }

    public Double getPracticalPercentage() {
        return percentage(practicalPresent, practicalSessions);
    }

    public Double getCombinedPercentage() {
        return percentage(getTotalPresent(), getTotalSessions());
    }

    private static Double percentage(int present, int total) {

        if (total == 0) {
            return null;
        }

        return Math.round(
                ((double) present / total) * 10000.0
        ) / 100.0;
    }
}
