package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AttendanceRecordDAO;
import lk.ac.ruhuna.ftms.dao.AttendanceSessionDAO;
import lk.ac.ruhuna.ftms.dao.MarkDAO;
import lk.ac.ruhuna.ftms.model.AssessmentMark;

import java.sql.SQLException;
import java.util.List;

public class EligibilityService {

    private final MarkDAO markDAO;
    private final AttendanceRecordDAO attendanceRecordDAO;
    private final AttendanceSessionDAO attendanceSessionDAO;

    public EligibilityService() {
        this.markDAO = new MarkDAO();
        this.attendanceRecordDAO = new AttendanceRecordDAO();
        this.attendanceSessionDAO = new AttendanceSessionDAO();
    }

    public double calculateCAWeightedScore(int enrollmentId) {

        try {
            List<AssessmentMark> marks =
                    markDAO.findCAMarksByEnrollment(enrollmentId);

            double total = 0;

            for (AssessmentMark item : marks) {

                if (item.getMarksObtained() != null &&
                        item.getMaxMark() > 0) {

                    total +=
                            (item.getMarksObtained() / item.getMaxMark())
                                    * item.getWeightPercentage();
                }
            }

            return round(total);

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to calculate CA score.",
                    e
            );
        }
    }

    public double calculateCAPercentage(int enrollmentId) {

        try {
            List<AssessmentMark> marks =
                    markDAO.findCAMarksByEnrollment(enrollmentId);

            double weightedScore = 0;
            double totalWeight = 0;

            for (AssessmentMark item : marks) {

                if (item.getMarksObtained() != null &&
                        item.getMaxMark() > 0) {

                    weightedScore +=
                            (item.getMarksObtained() / item.getMaxMark())
                                    * item.getWeightPercentage();

                    totalWeight += item.getWeightPercentage();
                }
            }

            if (totalWeight == 0) {
                return 0;
            }

            return round(
                    (weightedScore / totalWeight) * 100
            );

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to calculate CA percentage.",
                    e
            );
        }
    }

    public double calculateAttendancePercentage(int enrollmentId) {

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid enrollment ID."
            );
        }

        try {
            int totalSessions =
                    attendanceSessionDAO.countSessionsByEnrollment(
                            enrollmentId
                    );

            if (totalSessions == 0) {
                return 0.0;
            }

            int attendedSessions =
                    attendanceRecordDAO.countPresentByEnrollment(
                            enrollmentId
                    );

            double attendancePercentage =
                    ((double) attendedSessions / totalSessions) * 100;

            return round(attendancePercentage);

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to calculate attendance percentage.",
                    e
            );
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}