package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.CourseDAO;
import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class CourseService {

    private final CourseDAO courseDAO;
    private final BooleanSupplier isAdmin;

    /** Re-evaluated for every operation; never capture the user at screen creation. */
    public static boolean isCurrentUserAdmin() {
        var user = SessionManager.getCurrentUser();
        return user != null && user.isActive() && "ADMIN".equals(user.getRole());
    }

    public CourseService(
            CourseDAO courseDAO,
            BooleanSupplier isAdmin) {

        this.courseDAO = Objects.requireNonNull(courseDAO);
        this.isAdmin = Objects.requireNonNull(isAdmin);
    }

    private void requireAdmin() {
        if (!isAdmin.getAsBoolean()) {
            throw new SecurityException(
                    "Only administrators can manage courses.");
        }
    }

    public List<Course> getAllCourses() throws SQLException {
        requireAdmin();
        return courseDAO.findAll();
    }

    public List<Course> searchCourses(String keyword)
            throws SQLException {

        requireAdmin();

        if (keyword == null || keyword.trim().isEmpty()) {
            return courseDAO.findAll();
        }

        return courseDAO.search(keyword.trim());
    }

    public int createCourse(Course course) throws SQLException {
        requireAdmin();
        validate(course);

        return courseDAO.insert(course);
    }

    public boolean updateCourse(Course course) throws SQLException {
        requireAdmin();

        if (course == null || course.getCourseId() <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid course to update.");
        }

        validate(course);

        return courseDAO.update(course);
    }

    public boolean deleteCourse(int courseId) throws SQLException {
        requireAdmin();

        if (courseId <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid course to delete.");
        }

        return courseDAO.delete(courseId);
    }

    private void validate(Course course) {
        if (course == null) {
            throw new IllegalArgumentException(
                    "Course details are required.");
        }

        String code = course.getCourseCode() == null
                ? ""
                : course.getCourseCode().trim();

        String name = course.getCourseName() == null
                ? ""
                : course.getCourseName().trim();

        if (code.isEmpty() || code.length() > 30) {
            throw new IllegalArgumentException(
                    "Course code is required and must be at most 30 characters.");
        }

        if (name.isEmpty() || name.length() > 180) {
            throw new IllegalArgumentException(
                    "Course name is required and must be at most 180 characters.");
        }

        if (course.getCredits() == null
                || course.getCredits().compareTo(BigDecimal.ZERO) <= 0
                || course.getCredits()
                .compareTo(new BigDecimal("999.9")) > 0
                || course.getCredits().stripTrailingZeros().scale() > 1) {

            throw new IllegalArgumentException(
                    "Credits must be between 0.1 and 999.9 and have at most one decimal place.");
        }

        if (course.getDepartmentId() <= 0) {
            throw new IllegalArgumentException(
                    "Enter a valid department ID.");
        }

        String type = course.getCourseType() == null
                ? ""
                : course.getCourseType().trim().toUpperCase(Locale.ROOT);

        if (!type.equals("CORE")
                && !type.equals("ELECTIVE")
                && !type.equals("OPTIONAL")) {

            throw new IllegalArgumentException(
                    "Choose CORE, ELECTIVE, or OPTIONAL.");
        }

        course.setCourseCode(code);
        course.setCourseName(name);
        course.setCourseType(type);
    }
}
