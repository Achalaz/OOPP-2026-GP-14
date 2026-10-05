package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.SemesterResultDAO;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.GpaCourseRow;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.SemesterOption;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.StudentOption;
import lk.ac.ruhuna.ftms.model.SemesterResult;

import java.sql.SQLException;
import java.util.List;

public class GPAService {

    private final SemesterResultDAO semesterResultDAO;

    public GPAService() {
        this.semesterResultDAO = new SemesterResultDAO();
    }

    // Calculate SGPA + CGPA
    public SemesterResult calculateSemesterGPA(
            int studentId,
            int semesterId
    ) throws SQLException {

        validateIds(studentId, semesterId);

        List<GpaCourseRow> rows =
                semesterResultDAO.getCourseRowsForSemester(
                        studentId,
                        semesterId
                );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No course enrollments found for this student and semester."
            );
        }

        double totalRegisteredCredits = 0.0;
        double totalEarnedCredits = 0.0;
        double totalQualityPoints = 0.0;
        double gpaApplicableCredits = 0.0;

        for (GpaCourseRow row : rows) {

            double credits = row.credits();

            if (credits <= 0) {
                continue;
            }

            // All enrolled courses count as registered credits
            totalRegisteredCredits += credits;

            // GPA calculation uses published results only
            if (!row.published()) {
                continue;
            }

            if (row.gradePoint() == null) {
                continue;
            }

            // Current GPA-applicable statuses
            if (!isGpaApplicableStatus(
                    row.resultStatus()
            )) {
                continue;
            }

            gpaApplicableCredits += credits;

            double qualityPoints =
                    credits * row.gradePoint();

            totalQualityPoints += qualityPoints;

            // Earned credits only for passed courses
            if (row.passed()) {
                totalEarnedCredits += credits;
            }
        }

        Double sgpa = null;

        if (gpaApplicableCredits > 0) {
            sgpa = roundToTwoDecimals(
                    totalQualityPoints
                            / gpaApplicableCredits
            );
        }

        Double cgpa =
                calculateCGPA(
                        studentId,
                        semesterId
                );

        return new SemesterResult(
                studentId,
                semesterId,
                roundToTwoDecimals(
                        totalRegisteredCredits
                ),
                roundToTwoDecimals(
                        totalEarnedCredits
                ),
                roundToTwoDecimals(
                        totalQualityPoints
                ),
                sgpa,
                cgpa
        );
    }

    // Calculate CGPA up to selected semester
    public Double calculateCGPA(
            int studentId,
            int selectedSemesterId
    ) throws SQLException {

        validateIds(
                studentId,
                selectedSemesterId
        );

        List<Integer> semesterIds =
                semesterResultDAO
                        .getSemesterIdsByStudent(
                                studentId
                        );

        double cumulativeQualityPoints = 0.0;
        double cumulativeCredits = 0.0;

        boolean selectedSemesterReached = false;

        for (Integer semesterId : semesterIds) {

            List<GpaCourseRow> rows =
                    semesterResultDAO
                            .getCourseRowsForSemester(
                                    studentId,
                                    semesterId
                            );

            for (GpaCourseRow row : rows) {

                double credits = row.credits();

                if (credits <= 0) {
                    continue;
                }

                if (!row.published()) {
                    continue;
                }

                if (row.gradePoint() == null) {
                    continue;
                }

                if (!isGpaApplicableStatus(
                        row.resultStatus()
                )) {
                    continue;
                }

                cumulativeCredits += credits;

                cumulativeQualityPoints +=
                        credits * row.gradePoint();
            }

            if (semesterId == selectedSemesterId) {
                selectedSemesterReached = true;
                break;
            }
        }

        if (!selectedSemesterReached) {
            return null;
        }

        if (cumulativeCredits <= 0) {
            return null;
        }

        return roundToTwoDecimals(
                cumulativeQualityPoints
                        / cumulativeCredits
        );
    }

    // Save / update GPA result
    public void saveSemesterResult(
            SemesterResult result
    ) throws SQLException {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Semester result cannot be null."
            );
        }

        validateIds(
                result.getStudentId(),
                result.getSemesterId()
        );

        semesterResultDAO
                .saveOrUpdateSemesterResult(
                        result
                );
    }

    // Get stored semester result
    public SemesterResult getSemesterResult(
            int studentId,
            int semesterId
    ) throws SQLException {

        validateIds(
                studentId,
                semesterId
        );

        return semesterResultDAO
                .getByStudentAndSemester(
                        studentId,
                        semesterId
                );
    }

    // Get course rows for GPA table
    public List<GpaCourseRow> getCourseRows(
            int studentId,
            int semesterId
    ) throws SQLException {

        validateIds(
                studentId,
                semesterId
        );

        return semesterResultDAO
                .getCourseRowsForSemester(
                        studentId,
                        semesterId
                );
    }

    // Get previous stored semester results
    public List<SemesterResult>
    getSemesterResultsByStudent(
            int studentId
    ) throws SQLException {

        if (studentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid student ID."
            );
        }

        return semesterResultDAO
                .getSemesterResultsByStudent(
                        studentId
                );
    }

    // Load students for GPA GUI
    public List<StudentOption> getStudents()
            throws SQLException {

        return semesterResultDAO
                .getStudents();
    }

    // Load semesters for selected student
    public List<SemesterOption> getSemestersForStudent(
            int studentId
    ) throws SQLException {

        if (studentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid student ID."
            );
        }

        return semesterResultDAO
                .getSemestersForStudent(
                        studentId
                );
    }

    private boolean isGpaApplicableStatus(
            String resultStatus
    ) {

        if (resultStatus == null) {
            return false;
        }

        return resultStatus.equalsIgnoreCase("PASS")
                || resultStatus.equalsIgnoreCase("FAIL");
    }

    private void validateIds(
            int studentId,
            int semesterId
    ) {

        if (studentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid student ID."
            );
        }

        if (semesterId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid semester ID."
            );
        }
    }

    private double roundToTwoDecimals(
            double value
    ) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}