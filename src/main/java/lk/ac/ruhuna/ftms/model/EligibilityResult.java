package lk.ac.ruhuna.ftms.model;

public class EligibilityResult {

    private int enrollmentId;
    private double caPercentage;
    private boolean caEligible;
    private double attendancePercentage;
    private boolean attendanceEligible;
    private boolean overallEligible;
    private String reason;

    public EligibilityResult() {
    }

    public EligibilityResult(
            int enrollmentId,
            double caPercentage,
            boolean caEligible,
            double attendancePercentage,
            boolean attendanceEligible,
            boolean overallEligible,
            String reason) {

        this.enrollmentId = enrollmentId;
        this.caPercentage = caPercentage;
        this.caEligible = caEligible;
        this.attendancePercentage = attendancePercentage;
        this.attendanceEligible = attendanceEligible;
        this.overallEligible = overallEligible;
        this.reason = reason;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public double getCaPercentage() {
        return caPercentage;
    }

    public void setCaPercentage(double caPercentage) {
        this.caPercentage = caPercentage;
    }

    public boolean isCaEligible() {
        return caEligible;
    }

    public void setCaEligible(boolean caEligible) {
        this.caEligible = caEligible;
    }

    public double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public boolean isAttendanceEligible() {
        return attendanceEligible;
    }

    public void setAttendanceEligible(boolean attendanceEligible) {
        this.attendanceEligible = attendanceEligible;
    }

    public boolean isOverallEligible() {
        return overallEligible;
    }

    public void setOverallEligible(boolean overallEligible) {
        this.overallEligible = overallEligible;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}