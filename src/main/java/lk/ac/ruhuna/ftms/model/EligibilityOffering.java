package lk.ac.ruhuna.ftms.model;

/** Read-only course/offering choice for the eligibility screen. */
public record EligibilityOffering(
        int offeringId,
        String courseCode,
        String courseName,
        String offeringGroup) {

    @Override
    public String toString() {
        return courseCode + " - " + courseName
                + " (Offering " + offeringId + ", Group " + offeringGroup + ")";
    }
}
