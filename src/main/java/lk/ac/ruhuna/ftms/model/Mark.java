package lk.ac.ruhuna.ftms.model;

public class Mark {

    private int markId;
    private int enrollmentId;
    private int assessmentId;
    private Double marksObtained;
    private int attemptNo;
    private String markStatus;

    public String getMarkStatus() { return markStatus == null ? (marksObtained == null ? "PENDING" : "PRESENT") : markStatus; }
    public void setMarkStatus(String status) { this.markStatus = status; }

    public Mark() {
    }

    public Mark(int enrollmentId, int assessmentId,
                Double marksObtained, int attemptNo) {
        this.enrollmentId = enrollmentId;
        this.assessmentId = assessmentId;
        this.marksObtained = marksObtained;
        this.attemptNo = attemptNo;
    }

    public Mark(int markId, int enrollmentId, int assessmentId,
                Double marksObtained, int attemptNo) {
        this.markId = markId;
        this.enrollmentId = enrollmentId;
        this.assessmentId = assessmentId;
        this.marksObtained = marksObtained;
        this.attemptNo = attemptNo;
    }

    /** Validates the relationship between status and a nullable numeric score. */
    public void validateState() {
        if (!java.util.List.of("PRESENT","PENDING","ABSENT","MEDICAL").contains(getMarkStatus()))
            throw new lk.ac.ruhuna.ftms.exception.InvalidMarkException("Select a valid mark status.");
        if ("PRESENT".equals(getMarkStatus()) != (getMarksObtained() != null))
            throw new lk.ac.ruhuna.ftms.exception.InvalidMarkException("PRESENT requires a numeric mark; other statuses require an empty mark.");
        if (getMarksObtained() != null && (!Double.isFinite(getMarksObtained()) || getMarksObtained() < 0
                || java.math.BigDecimal.valueOf(getMarksObtained()).stripTrailingZeros().scale() > 2))
            throw new lk.ac.ruhuna.ftms.exception.InvalidMarkException("Marks must be nonnegative numbers with at most two decimal places.");
    }

    public int getMarkId() {
        return markId;
    }

    public void setMarkId(int markId) {
        this.markId = markId;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public int getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(int assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(Double marksObtained) {
        this.marksObtained = marksObtained;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }
}
