package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.MarkDAO;
import lk.ac.ruhuna.ftms.exception.InvalidMarkException;
import lk.ac.ruhuna.ftms.model.Mark;

import java.sql.SQLException;
import java.util.List;

public class MarkService {

    private final MarkDAO markDAO;

    public MarkService() {
        markDAO = new MarkDAO();
    }

    public void saveMark(Mark mark) {

        validate(mark);

        try {
            markDAO.saveOrUpdate(mark);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to save mark.", e
            );
        }
    }

    public List<Mark> getAllMarks() {

        try {
            return markDAO.findAll();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load marks.", e
            );
        }
    }

    public void deleteMark(int markId) {

        try {
            markDAO.delete(markId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to delete mark.", e
            );
        }
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

        Double value = mark.getMarksObtained();

        if (value != null &&
                (value < 0 || value > 100)) {

            throw new InvalidMarkException(
                    "Mark must be between 0 and 100."
            );
        }
    }
}