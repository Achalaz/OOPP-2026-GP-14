package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.ReportDAO;
import lk.ac.ruhuna.ftms.dao.ReportDAO.OfferingOption;
import lk.ac.ruhuna.ftms.dao.ReportDAO.StudentOption;
import lk.ac.ruhuna.ftms.model.ResultReportRow;

import java.sql.SQLException;
import java.util.List;

public class ReportService {

    private final ReportDAO reportDAO;

    public ReportService() {
        this.reportDAO = new ReportDAO();
    }

    // ---------------------------------------------------------
    // LOAD STUDENTS
    // ---------------------------------------------------------

    public List<StudentOption> getStudents()
            throws SQLException {

        return reportDAO.getStudents();
    }

    // ---------------------------------------------------------
    // LOAD COURSE OFFERINGS
    // ---------------------------------------------------------

    public List<OfferingOption> getCourseOfferings()
            throws SQLException {

        return reportDAO.getCourseOfferings();
    }

    // ---------------------------------------------------------
    // INDIVIDUAL STUDENT REPORT
    // ---------------------------------------------------------

    public List<ResultReportRow> getStudentReport(
            int studentId
    ) throws SQLException {

        if (studentId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid student ID."
            );
        }

        List<ResultReportRow> rows =
                reportDAO.getStudentResultReport(
                        studentId
                );

        if (rows.isEmpty()) {

            throw new IllegalArgumentException(
                    "No result records found for the selected student."
            );
        }

        return rows;
    }

    // ---------------------------------------------------------
    // BATCH / COURSE OFFERING REPORT
    // ---------------------------------------------------------

    public List<ResultReportRow> getOfferingReport(
            int offeringId
    ) throws SQLException {

        if (offeringId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid course offering ID."
            );
        }

        List<ResultReportRow> rows =
                reportDAO.getOfferingResultReport(
                        offeringId
                );

        if (rows.isEmpty()) {

            throw new IllegalArgumentException(
                    "No result records found for the selected course offering."
            );
        }

        return rows;
    }

    // ---------------------------------------------------------
    // SIMPLE REPORT STATISTICS
    // ---------------------------------------------------------

    public int countTotalResults(
            List<ResultReportRow> rows
    ) {

        if (rows == null) {
            return 0;
        }

        return rows.size();
    }

    public int countPassed(
            List<ResultReportRow> rows
    ) {

        if (rows == null) {
            return 0;
        }

        int count = 0;

        for (ResultReportRow row : rows) {

            if (row.getResultStatus() != null
                    && row.getResultStatus()
                    .equalsIgnoreCase("PASS")) {

                count++;
            }
        }

        return count;
    }

    public int countFailed(
            List<ResultReportRow> rows
    ) {

        if (rows == null) {
            return 0;
        }

        int count = 0;

        for (ResultReportRow row : rows) {

            if (row.getResultStatus() != null
                    && row.getResultStatus()
                    .equalsIgnoreCase("FAIL")) {

                count++;
            }
        }

        return count;
    }

    public int countPending(
            List<ResultReportRow> rows
    ) {

        if (rows == null) {
            return 0;
        }

        int count = 0;

        for (ResultReportRow row : rows) {

            String status =
                    row.getResultStatus();

            if (status == null
                    || status.equalsIgnoreCase("PENDING")
                    || status.equalsIgnoreCase("INCOMPLETE")) {

                count++;
            }
        }

        return count;
    }

    public Double calculateAverageOverallMark(
            List<ResultReportRow> rows
    ) {

        if (rows == null || rows.isEmpty()) {
            return null;
        }

        double total = 0.0;
        int count = 0;

        for (ResultReportRow row : rows) {

            if (row.getOverallMark() != null) {

                total += row.getOverallMark();
                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        return roundToTwoDecimals(
                total / count
        );
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    private double roundToTwoDecimals(
            double value
    ) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}