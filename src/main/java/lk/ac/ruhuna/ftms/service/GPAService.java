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

    public SemesterResult calculateSemesterGPA(
            int studentId,
            int semesterId
    ) throws SQLException {

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

        // Credits actually used in SGPA denominator
        double gpaApplicableCredits = 0.0;

        for (GpaCourseRow row : rows) {

            double credits = row.credits();

            if (credits <= 0) {
                continue;
            }

            // All enrolled course credits
            totalRegisteredCredits += credits;

            /*
             * Do not use draft/unpublished results
             * for GPA calculation.
             */
            if (!row.published()) {
                continue;
            }

            /*
             * A grade point must exist before
             * quality points can be calculated.
             */
            if (row.gradePoint() == null) {
                continue;
            }

            /*
             * For the current implementation,
             * only completed PASS / FAIL results
             * participate in GPA calculation.
             *
             * PENDING, MEDICAL, ABSENT,
             * NOT_ELIGIBLE and INCOMPLETE are
             * not included here.
             */
            if (!isGpaApplicableStatus(row.resultStatus())) {
                continue;
            }

            gpaApplicableCredits += credits;

            double qualityPoints =
                    credits * row.gradePoint();

            totalQualityPoints += qualityPoints;

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

        totalRegisteredCredits =
                roundToTwoDecimals(totalRegisteredCredits);

        totalEarnedCredits =
                roundToTwoDecimals(totalEarnedCredits);

        totalQualityPoints =
                roundToTwoDecimals(totalQualityPoints);

        SemesterResult result =
                new SemesterResult(
                        studentId,
                        semesterId,
                        totalRegisteredCredits,
                        totalEarnedCredits,
                        totalQualityPoints,
                        sgpa,
                        null
                );

        return result;
    }

    public void saveSemesterResult(
            SemesterResult result
    ) throws SQLException {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Semester result cannot be null."
            );
        }

        semesterResultDAO
                .saveOrUpdateSemesterResult(result);
    }

    public SemesterResult getSemesterResult(
            int studentId,
            int semesterId
    ) throws SQLException {

        return semesterResultDAO
                .getByStudentAndSemester(
                        studentId,
                        semesterId
                );
    }

    public List<GpaCourseRow> getCourseRows(
            int studentId,
            int semesterId
    ) throws SQLException {

        return semesterResultDAO
                .getCourseRowsForSemester(
                        studentId,
                        semesterId
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

    private double roundToTwoDecimals(
            double value
    ) {
        return Math.round(value * 100.0) / 100.0;
    }
}