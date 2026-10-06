package lk.ac.ruhuna.ftms.model;

public class ResultReportRow {

    private int enrollmentId;

    private String registrationNo;
    private String studentName;

    private String courseCode;
    private String courseName;

    private double credits;

    private Double caMark;
    private Double finalExamMark;
    private Double overallMark;

    private String gradeLetter;
    private Double gradePoint;

    private String resultStatus;

    public ResultReportRow() {
    }

    public ResultReportRow(
            int enrollmentId,
            String registrationNo,
            String studentName,
            String courseCode,
            String courseName,
            double credits,
            Double caMark,
            Double finalExamMark,
            Double overallMark,
            String gradeLetter,
            Double gradePoint,
            String resultStatus
    ) {

        this.enrollmentId = enrollmentId;
        this.registrationNo = registrationNo;
        this.studentName = studentName;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.caMark = caMark;
        this.finalExamMark = finalExamMark;
        this.overallMark = overallMark;
        this.gradeLetter = gradeLetter;
        this.gradePoint = gradePoint;
        this.resultStatus = resultStatus;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(
            String registrationNo
    ) {
        this.registrationNo = registrationNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(
            String studentName
    ) {
        this.studentName = studentName;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(
            String courseCode
    ) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(
            String courseName
    ) {
        this.courseName = courseName;
    }

    public double getCredits() {
        return credits;
    }

    public void setCredits(
            double credits
    ) {
        this.credits = credits;
    }

    public Double getCaMark() {
        return caMark;
    }

    public void setCaMark(
            Double caMark
    ) {
        this.caMark = caMark;
    }

    public Double getFinalExamMark() {
        return finalExamMark;
    }

    public void setFinalExamMark(
            Double finalExamMark
    ) {
        this.finalExamMark = finalExamMark;
    }

    public Double getOverallMark() {
        return overallMark;
    }

    public void setOverallMark(
            Double overallMark
    ) {
        this.overallMark = overallMark;
    }

    public String getGradeLetter() {
        return gradeLetter;
    }

    public void setGradeLetter(
            String gradeLetter
    ) {
        this.gradeLetter = gradeLetter;
    }

    public Double getGradePoint() {
        return gradePoint;
    }

    public void setGradePoint(
            Double gradePoint
    ) {
        this.gradePoint = gradePoint;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(
            String resultStatus
    ) {
        this.resultStatus = resultStatus;
    }
}