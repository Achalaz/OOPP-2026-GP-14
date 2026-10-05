package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.SemesterResultDAO;
import lk.ac.ruhuna.ftms.dao.SemesterResultDAO.GpaCourseRow;
import lk.ac.ruhuna.ftms.model.SemesterResult;

import java.sql.SQLException;
import java.util.List;

public class GPAService {

    private final SemesterResultDAO semesterResultDAO;

    public GPAService() {
        this.semesterResultDAO = new SemesterResultDAO();
    }

    // Calculate SGPA + CGPA for one student and semester
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

            // Every enrolled course contributes to registered credits
            totalRegisteredCredits += credits;

            // GPA calculation uses only published results
            if (!row.published()) {
                continue;
            }

            // Cannot calculate quality points without a grade point
            if (row.gradePoint() == null) {
                continue;
            }

            // Only completed PASS / FAIL results are GPA-applicable here
            if (!isGpaApplicableStatus(row.resultStatus())) {
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

    // Calculate cumulative GPA up to selected semester
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

        for (Integer semesterId : semesterIds) {

            /*
             * Current implementation assumes semester_id order
             * follows chronological order.
             *
             * This can later be replaced with academic-year /
             * semester ordering if required by the final schema.
             */
            if (semesterId > selectedSemesterId) {
                continue;
            }

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
        }

        if (cumulativeCredits <= 0) {
            return null;
        }

        return roundToTwoDecimals(
                cumulativeQualityPoints
                        / cumulativeCredits
        );
    }

    // Save or update GPA result in semester_results table
    public void saveSemesterResult(
            SemesterResult result
    ) throws SQLException {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Semester result cannot be null."
            );
        }

        if (result.getStudentId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid student ID."
            );
        }

        if (result.getSemesterId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid semester ID."
            );
        }

        semesterResultDAO
                .saveOrUpdateSemesterResult(
                        result
                );
    }

    // Get previously stored GPA result
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

    // Used by GPA GUI table
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

    // Get all previously stored semester results
    public List<SemesterResult> getSemesterResultsByStudent(
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