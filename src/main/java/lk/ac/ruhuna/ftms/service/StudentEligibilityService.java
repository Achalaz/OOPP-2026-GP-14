
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.StudentEligibilityDAO;
import lk.ac.ruhuna.ftms.dao.StudentEligibilityDAO.StudentEligibility;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Read-only student eligibility service.
 * Students can view only their own eligibility records.
 */
public final class StudentEligibilityService {

    private final StudentEligibilityDAO eligibilityDAO;
    private final SessionIdentity loginIdentity;

    // Default constructor
    public StudentEligibilityService() {
        this(new StudentEligibilityDAO());
    }

    // Constructor with dependency injection
    public StudentEligibilityService(
            StudentEligibilityDAO eligibilityDAO) {

        this.eligibilityDAO = Objects.requireNonNull(
                eligibilityDAO,
                "StudentEligibilityDAO cannot be null."
        );

        // Capture current login session
        this.loginIdentity = SessionManager.requireSession();

        // Verify undergraduate student
        requireLoggedInStudentId();
    }

    /**
     * Get eligibility records for the logged-in student.
     * Includes pending records for enrolled courses.
     */
    public List<StudentEligibility> getMyEligibility()
            throws SQLException {

        // Student ID comes from login session
        int studentId = requireLoggedInStudentId();

        // Load only this student's course eligibility
        List<StudentEligibility> rows =
                eligibilityDAO.findByStudentId(studentId);

        // Verify that the login has not changed
        requireLoggedInStudentId();

        // Return read-only list
        return List.copyOf(rows);
    }

    /**
     * Verify current logged-in user is an active student.
     * Never accept a student ID from the GUI.
     */
    private int requireLoggedInStudentId() {

        // Verify the original session is still active
        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "Only a signed-in undergraduate can view their eligibility."
            );
        }

        return loginIdentity.userId();
    }
}
