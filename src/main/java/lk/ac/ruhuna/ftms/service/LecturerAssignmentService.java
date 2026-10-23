package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO.LecturerOption;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Admin assignment management, bound to the login that opened the screen. */
public class LecturerAssignmentService {
    private final LecturerCourseAssignmentDAO dao;
    private final SessionManager.SessionIdentity identity;

    public LecturerAssignmentService(LecturerCourseAssignmentDAO dao) {
        requireActiveAdmin();
        this.dao = Objects.requireNonNull(dao);
        identity = SessionManager.requireSession();
    }

    private static void requireActiveAdmin() {
        if (!CourseService.isCurrentUserAdmin()) {
            throw new SecurityException("Only active Admin users can manage lecturer assignments.");
        }
    }

    private void requireAdmin() {
        SessionManager.requireSession(identity);
        requireActiveAdmin();
    }

    public List<LecturerOption> getEligibleLecturers() throws SQLException {
        requireAdmin();
        return dao.getEligibleLecturers(identity);
    }

    public List<LecturerCourseAssignment> getAssignmentsByOffering(int offeringId) throws SQLException {
        requireAdmin(); requireId(offeringId, "Select an existing offering.");
        return dao.getAssignmentsByOffering(offeringId, identity);
    }

    public int assignLecturer(int offeringId, int lecturerId, AssignmentType type) throws SQLException {
        requireAdmin(); requireId(offeringId, "Select an existing offering.");
        requireId(lecturerId, "Select an active lecturer."); requireType(type);
        return dao.assignLecturer(offeringId, lecturerId, type, identity);
    }

    public void updateAssignmentType(int offeringId, int assignmentId, AssignmentType type) throws SQLException {
        requireAdmin(); requireIds(offeringId, assignmentId); requireType(type);
        dao.updateAssignmentType(offeringId, assignmentId, type, identity);
    }

    public void removeAssignment(int offeringId, int assignmentId) throws SQLException {
        requireAdmin(); requireIds(offeringId, assignmentId);
        dao.removeAssignment(offeringId, assignmentId, identity);
    }

    /** A preview for the confirmation dialog. Mutations repeat this check under locks. */
    public String getAssignmentChangeBlocker(int offeringId, int assignmentId) throws SQLException {
        requireAdmin(); requireIds(offeringId, assignmentId);
        return dao.getAssignmentChangeBlocker(offeringId, assignmentId, identity);
    }

    private static void requireIds(int offeringId, int assignmentId) {
        requireId(offeringId, "Select an existing offering.");
        requireId(assignmentId, "Select an assigned lecturer.");
    }
    private static void requireId(int id, String message) {
        if (id <= 0) throw new IllegalArgumentException(message);
    }
    private static void requireType(AssignmentType type) {
        if (type == null) throw new IllegalArgumentException("Select PRIMARY, ASSISTANT or PRACTICAL.");
    }
}
