package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.MarkDAO;
import lk.ac.ruhuna.ftms.model.AssessmentMark;
import lk.ac.ruhuna.ftms.model.EligibilityResult;

import java.sql.SQLException;
import java.util.List;
public class EligibilityService {

    // Confirmed SRS rule
    private static final double CA_ELIGIBILITY_THRESHOLD = 40.0;

    private final MarkDAO markDAO;

    public EligibilityService() {
        this.markDAO = new MarkDAO();
    }

    // ---------------------------------------------------------
    // CALCULATE RAW WEIGHTED CA SCORE
    // ---------------------------------------------------------
    public double calculateCAWeightedScore(int enrollmentId) {

        try {
            List<AssessmentMark> marks =
                    markDAO.findCAMarksByEnrollment(enrollmentId);

            double total = 0;

            for (AssessmentMark item : marks) {

                if (item.getMarksObtained() != null
                        && item.getMaxMark() > 0) {

                    total +=
                            (item.getMarksObtained()
                                    / item.getMaxMark())
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

    // ---------------------------------------------------------
    // CALCULATE CA PERCENTAGE
    // ---------------------------------------------------------
    public double calculateCAPercentage(int enrollmentId) {

        try {
            List<AssessmentMark> marks =
                    markDAO.findCAMarksByEnrollment(enrollmentId);

            double weightedScore = 0;
            double totalWeight = 0;

            for (AssessmentMark item : marks) {

                if (item.getMarksObtained() != null
                        && item.getMaxMark() > 0) {

                    weightedScore +=
                            (item.getMarksObtained()
                                    / item.getMaxMark())
                                    * item.getWeightPercentage();

                    totalWeight +=
                            item.getWeightPercentage();
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

    // ---------------------------------------------------------
    // CA ELIGIBILITY
    // CA >= 40%
    // ---------------------------------------------------------
    public boolean isCAEligible(int enrollmentId) {

        double caPercentage =
                calculateCAPercentage(enrollmentId);

        return caPercentage >=
                CA_ELIGIBILITY_THRESHOLD;
    }

    // ---------------------------------------------------------
    // COMPLETE ELIGIBILITY RESULT
    //
    // Attendance eligibility is passed from the Attendance
    // module because the official attendance threshold is TBC.
    // ---------------------------------------------------------
    public EligibilityResult evaluateEligibility(
            int enrollmentId,
            double attendancePercentage,
            boolean attendanceEligible) {

        double caPercentage =
                calculateCAPercentage(enrollmentId);

        boolean caEligible =
                caPercentage >= CA_ELIGIBILITY_THRESHOLD;

        boolean overallEligible =
                caEligible && attendanceEligible;

        String reason =
                buildReason(
                        caEligible,
                        attendanceEligible
                );

        return new EligibilityResult(
                enrollmentId,
                caPercentage,
                caEligible,
                round(attendancePercentage),
                attendanceEligible,
                overallEligible,
                reason
        );
    }

    // ---------------------------------------------------------
    // RESULT REASON
    // ---------------------------------------------------------
    private String buildReason(
            boolean caEligible,
            boolean attendanceEligible) {

        if (caEligible && attendanceEligible) {
            return "Eligible";
        }

        if (!caEligible && !attendanceEligible) {
            return "CA and attendance requirements not met.";
        }

        if (!caEligible) {
            return "CA requirement not met.";
        }

        return "Attendance requirement not met.";
    }

    // ---------------------------------------------------------
    // ROUND TO 2 DECIMAL PLACES
    // ---------------------------------------------------------
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}