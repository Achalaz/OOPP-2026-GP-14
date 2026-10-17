package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.AssessmentDAO;
import lk.ac.ruhuna.ftms.dao.MarkDAO;
import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.model.Mark;

import java.sql.SQLException;
import java.util.List;

public class MarkService {

    private final MarkDAO markDAO;
    private final AssessmentDAO assessmentDAO;

    public MarkService() {
        markDAO = new MarkDAO();
        assessmentDAO = new AssessmentDAO();
    }

    public void saveMark(Mark mark) {

        requireStaff();
        var identity = lk.ac.ruhuna.ftms.util.SessionManager.requireSession();
        boolean lecturer = "LECTURER".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole());
        if (mark == null) throw new InvalidMarkException("Mark is required.");
        if ("LECTURER".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole())) {
            try {
                int offering = new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO()
                        .enrollmentOffering(mark.getEnrollmentId(), mark.getAssessmentId(), mark.getAttemptNo());
                new LecturerAccessService().requireOffering(offering);
            } catch (SQLException e) { throw new IllegalStateException("Cannot verify course assignment.", e); }
        }
        validate(mark);

        try {
            lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);
            if (lecturer) markDAO.saveLecturerMark(mark, identity);
            else markDAO.saveOrUpdate(mark);

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to save mark.",
                    e
            );
        }
    }

    public List<Mark> getAllMarks() {
        requireStaff();
        if ("LECTURER".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole()))
            throw new SecurityException("Use the assigned-course marks view.");

        try {
            return markDAO.findAll();

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to load marks.",
                    e
            );
        }
    }

    public void deleteMark(int markId) {
        requireStaff();
        if (!"ADMIN".equals(lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser().getRole()))
            throw new SecurityException("Lecturers may update marks, but cannot delete them.");

        try {
            markDAO.delete(markId);

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to delete mark.",
                    e
            );
        }
    }

    private void requireStaff() {
        var user = lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !("ADMIN".equals(user.getRole()) || "LECTURER".equals(user.getRole())))
            throw new SecurityException("Authenticated teaching staff required.");
    }

    private void validate(Mark mark) {

        if (mark.getEnrollmentId() <= 0) {
            throw new InvalidMarkException(
                    "Valid enrollment ID is required."
            );
        }

        if (mark.getAssessmentId() <= 0) {
            throw new InvalidMarkException(
                    "Valid assessment ID is required."
            );
        }

        if (mark.getAttemptNo() <= 0) {
            throw new InvalidMarkException(
                    "Attempt number must be greater than zero."
            );
        }

        Double maxMark;

        try {

            maxMark = assessmentDAO.getMaxMarkById(
                    mark.getAssessmentId()
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to read assessment details.",
                    e
            );
        }

        if (maxMark == null) {
            throw new InvalidMarkException(
                    "Assessment ID does not exist."
            );
        }

        Double value = mark.getMarksObtained();

        if (value != null &&
                (!Double.isFinite(value) || value < 0 || value > maxMark)) {

            throw new InvalidMarkException(
                    "Mark must be between 0 and " + maxMark + "."
            );
        }
    }
}
