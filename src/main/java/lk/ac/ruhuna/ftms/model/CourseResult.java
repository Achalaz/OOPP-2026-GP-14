package lk.ac.ruhuna.ftms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Maps course_results exactly. Nullable marks and grades remain unknown, not zero. */
public class CourseResult {
    public enum ResultStatus { PENDING, PASS, FAIL, ABSENT, MEDICAL, NOT_ELIGIBLE, INCOMPLETE }

    private int resultId;
    private int enrollmentId;
    private BigDecimal caMark;
    private BigDecimal finalExamMark;
    private BigDecimal overallMark;
    private Integer gradeRuleId;
    private String gradeLetter;
    private BigDecimal gradePoint;
    private ResultStatus resultStatus = ResultStatus.PENDING;
    private boolean published;
    private LocalDateTime calculatedAt;
    private LocalDateTime publishedAt;
    private Integer calculatedBy;

    public int getResultId() { return resultId; }
    public void setResultId(int resultId) { this.resultId = resultId; }
    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }
    public BigDecimal getCaMark() { return caMark; }
    public void setCaMark(BigDecimal caMark) { this.caMark = caMark; }
    public BigDecimal getFinalExamMark() { return finalExamMark; }
    public void setFinalExamMark(BigDecimal finalExamMark) { this.finalExamMark = finalExamMark; }
    public BigDecimal getOverallMark() { return overallMark; }
    public void setOverallMark(BigDecimal overallMark) { this.overallMark = overallMark; }
    public Integer getGradeRuleId() { return gradeRuleId; }
    public void setGradeRuleId(Integer gradeRuleId) { this.gradeRuleId = gradeRuleId; }
    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal gradePoint) { this.gradePoint = gradePoint; }
    public ResultStatus getResultStatus() { return resultStatus; }
    public void setResultStatus(ResultStatus resultStatus) { this.resultStatus = resultStatus; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Integer getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(Integer calculatedBy) { this.calculatedBy = calculatedBy; }
}
