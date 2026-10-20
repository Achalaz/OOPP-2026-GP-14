
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.StudentAttendanceDAO;
import lk.ac.ruhuna.ftms.dao.StudentAttendanceDAO.CourseAttendance;
import lk.ac.ruhuna.ftms.dao.StudentAttendanceDAO.SessionAttendance;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StudentAttendanceService {

    private final StudentAttendanceDAO attendanceDAO;
    private final SessionIdentity loginIdentity;

    // Default constructor
    public StudentAttendanceService() {
        this(new StudentAttendanceDAO());
    }

    // Constructor with dependency injection
    public StudentAttendanceService(
            StudentAttendanceDAO attendanceDAO) {

        this.attendanceDAO = Objects.requireNonNull(
                attendanceDAO,
                "StudentAttendanceDAO cannot be null."
        );

        // Get current login session
        this.loginIdentity = SessionManager.requireSession();

        // Verify undergraduate student
        requireLoggedInStudent();
    }

    // Get attendance summary for logged-in student
    public List<CourseAttendance> getMyCourseAttendance()
            throws SQLException {

        int studentId = requireLoggedInStudent();

        List<CourseAttendance> results =
                attendanceDAO.findCourseAttendance(studentId);

        // Verify session after database operation
        requireLoggedInStudent();

        return List.copyOf(results);
    }

    // Get attendance sessions for selected enrollment
    public List<SessionAttendance> getMySessionAttendance(
            int enrollmentId) throws SQLException {

        int studentId = requireLoggedInStudent();

        if (enrollmentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid enrollment ID."
            );
        }

        // Verify that enrollment belongs to this student
        boolean ownedEnrollment =
                attendanceDAO.findCourseAttendance(studentId)
                        .stream()
                        .anyMatch(
                                course ->
                                        course.enrollmentId()
                                                == enrollmentId
                        );

        requireLoggedInStudent();

        if (!ownedEnrollment) {
            throw new SecurityException(
                    "You can only view attendance for your own courses."
            );
        }

        // Load attendance sessions for own enrollment
        List<SessionAttendance> results =
                attendanceDAO.findSessionAttendance(
                        studentId,
                        enrollmentId
                );

        // Verify session again after database operation
        requireLoggedInStudent();

        return List.copyOf(results);
    }

    // Verify current logged-in student
    private int requireLoggedInStudent() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole())
                || user.getUserId() != loginIdentity.userId()) {

            throw new SecurityException(
                    "Only a signed-in undergraduate can view this attendance."
            );
        }

        return loginIdentity.userId();
    }
}
