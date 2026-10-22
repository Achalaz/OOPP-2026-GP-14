package lk.ac.ruhuna.ftms.model;

public final class LecturerCourseAssignment {
    public enum AssignmentType { PRIMARY, ASSISTANT, PRACTICAL }

    private final int assignmentId;
    private final int offeringId;
    private final int lecturerId;
    private final String lecturerName;
    private final String staffNo;
    private final AssignmentType assignmentType;
    private final String courseCode;
    private final String offeringGroup;
    private final String semester;

    public LecturerCourseAssignment(int assignmentId, int offeringId, int lecturerId,
            String lecturerName, String staffNo, AssignmentType assignmentType,
            String courseCode, String offeringGroup, String semester) {
        this.assignmentId = assignmentId;
        this.offeringId = offeringId;
        this.lecturerId = lecturerId;
        this.lecturerName = lecturerName;
        this.staffNo = staffNo;
        this.assignmentType = assignmentType;
        this.courseCode = courseCode;
        this.offeringGroup = offeringGroup;
        this.semester = semester;
    }

    public int getAssignmentId() { return assignmentId; }
    public int getOfferingId() { return offeringId; }
    public int getLecturerId() { return lecturerId; }
    public String getLecturerName() { return lecturerName; }
    public String getStaffNo() { return staffNo; }
    public AssignmentType getAssignmentType() { return assignmentType; }
    public String getCourseCode() { return courseCode; }
    public String getOfferingGroup() { return offeringGroup; }
    public String getSemester() { return semester; }
}
