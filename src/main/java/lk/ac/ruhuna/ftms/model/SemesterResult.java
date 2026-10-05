package lk.ac.ruhuna.ftms.model;

import java.time.LocalDateTime;

public class SemesterResult {

    private int semesterResultId;
    private int studentId;
    private int semesterId;
    private double totalRegisteredCredits;
    private double totalEarnedCredits;
    private double totalQualityPoints;
    private Double sgpa;
    private Double cgpa;
    private LocalDateTime calculatedAt;

    public SemesterResult() {
    }

    public SemesterResult(
            int semesterResultId,
            int studentId,
            int semesterId,
            double totalRegisteredCredits,
            double totalEarnedCredits,
            double totalQualityPoints,
            Double sgpa,
            Double cgpa,
            LocalDateTime calculatedAt) {

        this.semesterResultId = semesterResultId;
        this.studentId = studentId;
        this.semesterId = semesterId;
        this.totalRegisteredCredits = totalRegisteredCredits;
        this.totalEarnedCredits = totalEarnedCredits;
        this.totalQualityPoints = totalQualityPoints;
        this.sgpa = sgpa;
        this.cgpa = cgpa;
        this.calculatedAt = calculatedAt;
    }

    public SemesterResult(
            int studentId,
            int semesterId,
            double totalRegisteredCredits,
            double totalEarnedCredits,
            double totalQualityPoints,
            Double sgpa,
            Double cgpa) {

        this.studentId = studentId;
        this.semesterId = semesterId;
        this.totalRegisteredCredits = totalRegisteredCredits;
        this.totalEarnedCredits = totalEarnedCredits;
        this.totalQualityPoints = totalQualityPoints;
        this.sgpa = sgpa;
        this.cgpa = cgpa;
    }

    public int getSemesterResultId() {
        return semesterResultId;
    }

    public void setSemesterResultId(int semesterResultId) {
        this.semesterResultId = semesterResultId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(int semesterId) {
        this.semesterId = semesterId;
    }

    public double getTotalRegisteredCredits() {
        return totalRegisteredCredits;
    }

    public void setTotalRegisteredCredits(double totalRegisteredCredits) {
        this.totalRegisteredCredits = totalRegisteredCredits;
    }

    public double getTotalEarnedCredits() {
        return totalEarnedCredits;
    }

    public void setTotalEarnedCredits(double totalEarnedCredits) {
        this.totalEarnedCredits = totalEarnedCredits;
    }

    public double getTotalQualityPoints() {
        return totalQualityPoints;
    }

    public void setTotalQualityPoints(double totalQualityPoints) {
        this.totalQualityPoints = totalQualityPoints;
    }

    public Double getSgpa() {
        return sgpa;
    }

    public void setSgpa(Double sgpa) {
        this.sgpa = sgpa;
    }

    public Double getCgpa() {
        return cgpa;
    }

    public void setCgpa(Double cgpa) {
        this.cgpa = cgpa;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}