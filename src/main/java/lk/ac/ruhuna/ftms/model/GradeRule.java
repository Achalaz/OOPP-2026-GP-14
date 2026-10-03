package lk.ac.ruhuna.ftms.model;

import java.math.BigDecimal;

/** A database-configured grade range; sample rows are not official grading policy. */
public class GradeRule {
    private int gradeRuleId;
    private String gradeLetter;
    private BigDecimal minMark;
    private BigDecimal maxMark;
    private BigDecimal gradePoint;
    private boolean isPass;
    private boolean isActive;

    public GradeRule() { }

    public GradeRule(int gradeRuleId, String gradeLetter, BigDecimal minMark,
                     BigDecimal maxMark, BigDecimal gradePoint, boolean isPass, boolean isActive) {
        this.gradeRuleId = gradeRuleId;
        this.gradeLetter = gradeLetter;
        this.minMark = minMark;
        this.maxMark = maxMark;
        this.gradePoint = gradePoint;
        this.isPass = isPass;
        this.isActive = isActive;
    }

    public int getGradeRuleId() { return gradeRuleId; }
    public void setGradeRuleId(int gradeRuleId) { this.gradeRuleId = gradeRuleId; }
    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }
    public BigDecimal getMinMark() { return minMark; }
    public void setMinMark(BigDecimal minMark) { this.minMark = minMark; }
    public BigDecimal getMaxMark() { return maxMark; }
    public void setMaxMark(BigDecimal maxMark) { this.maxMark = maxMark; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal gradePoint) { this.gradePoint = gradePoint; }
    public boolean isPass() { return isPass; }
    public void setPass(boolean pass) { isPass = pass; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
