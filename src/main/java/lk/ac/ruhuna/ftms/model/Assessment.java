package lk.ac.ruhuna.ftms.model;

import java.time.LocalDate;

public class Assessment {
    @Override public String toString() { return assessmentId + " - " + assessmentName + " (max " + maxMark + ")"; }

    private int assessmentId;
    private int offeringId;
    private String assessmentName;
    private String assessmentType;
    private double maxMark;
    private double weightPercentage;
    private LocalDate assessmentDate;
    private String description;

    public Assessment() {
    }

    public Assessment(int assessmentId,
                      int offeringId,
                      String assessmentName,
                      String assessmentType,
                      double maxMark,
                      double weightPercentage,
                      LocalDate assessmentDate,
                      String description) {

        this.assessmentId = assessmentId;
        this.offeringId = offeringId;
        this.assessmentName = assessmentName;
        this.assessmentType = assessmentType;
        this.maxMark = maxMark;
        this.weightPercentage = weightPercentage;
        this.assessmentDate = assessmentDate;
        this.description = description;
    }

    public Assessment(int offeringId,
                      String assessmentName,
                      String assessmentType,
                      double maxMark,
                      double weightPercentage,
                      LocalDate assessmentDate,
                      String description) {

        this.offeringId = offeringId;
        this.assessmentName = assessmentName;
        this.assessmentType = assessmentType;
        this.maxMark = maxMark;
        this.weightPercentage = weightPercentage;
        this.assessmentDate = assessmentDate;
        this.description = description;
    }

    public int getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(int assessmentId) {
        this.assessmentId = assessmentId;
    }

    public int getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(int offeringId) {
        this.offeringId = offeringId;
    }

    public String getAssessmentName() {
        return assessmentName;
    }

    public void setAssessmentName(String assessmentName) {
        this.assessmentName = assessmentName;
    }

    public String getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
    }

    public double getMaxMark() {
        return maxMark;
    }

    public void setMaxMark(double maxMark) {
        this.maxMark = maxMark;
    }

    public double getWeightPercentage() {
        return weightPercentage;
    }

    public void setWeightPercentage(double weightPercentage) {
        this.weightPercentage = weightPercentage;
    }

    public LocalDate getAssessmentDate() {
        return assessmentDate;
    }

    public void setAssessmentDate(LocalDate assessmentDate) {
        this.assessmentDate = assessmentDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}