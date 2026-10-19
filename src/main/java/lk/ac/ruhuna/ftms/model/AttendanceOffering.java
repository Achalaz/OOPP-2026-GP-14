package lk.ac.ruhuna.ftms.model;

/** Read-only choice; course offering CRUD remains in its existing module. */
public record AttendanceOffering(int offeringId, String courseCode, String courseName,
                                 int semesterId, String offeringGroup) {
    @Override public String toString() {
        return courseCode + " - " + courseName + " | Semester " + semesterId
                + " / " + offeringGroup + " (#" + offeringId + ")";
    }
}
