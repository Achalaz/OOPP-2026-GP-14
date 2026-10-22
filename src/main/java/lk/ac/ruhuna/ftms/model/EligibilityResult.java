package lk.ac.ruhuna.ftms.model;

public class EligibilityResult {

    private int enrollmentId;
    private Double caPercentage;
    private Boolean caEligible;
    private Double attendancePercentage;
    private Boolean attendanceEligible;
    private Boolean overallEligible;
    private String reason;

    public EligibilityResult() {
    }

    public EligibilityResult(
            int enrollmentId,
            Double caPercentage,
            Boolean caEligible,
            Double attendancePercentage,
            Boolean attendanceEligible,
            Boolean overallEligible,
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

    public Double getCaPercentage() {
        return caPercentage;
    }

    public void setCaPercentage(Double caPercentage) {
        this.caPercentage = caPercentage;
    }

    public Boolean isCaEligible() {
        return caEligible;
    }

    public void setCaEligible(Boolean caEligible) {
        this.caEligible = caEligible;
    }

    public Double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(Double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public Boolean isAttendanceEligible() {
        return attendanceEligible;
    }

    public void setAttendanceEligible(Boolean attendanceEligible) {
        this.attendanceEligible = attendanceEligible;
    }

    public Boolean isOverallEligible() {
        return overallEligible;
    }

    public void setOverallEligible(Boolean overallEligible) {
        this.overallEligible = overallEligible;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}