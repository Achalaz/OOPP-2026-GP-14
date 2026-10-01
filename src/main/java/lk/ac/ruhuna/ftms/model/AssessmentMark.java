package lk.ac.ruhuna.ftms.model;

public class AssessmentMark {

    private Double marksObtained;
    private double maxMark;
    private double weightPercentage;

    public AssessmentMark(Double marksObtained,
                          double maxMark,
                          double weightPercentage) {

        this.marksObtained = marksObtained;
        this.maxMark = maxMark;
        this.weightPercentage = weightPercentage;
    }

    public Double getMarksObtained() {
        return marksObtained;
    }

    public double getMaxMark() {
        return maxMark;
    }

    public double getWeightPercentage() {
        return weightPercentage;
    }
}