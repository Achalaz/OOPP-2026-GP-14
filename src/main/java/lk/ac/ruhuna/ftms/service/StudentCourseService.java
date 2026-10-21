package lk.ac.ruhuna.ftms.service;

import java.sql.SQLException;
import java.util.List;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse;

public final class StudentCourseService {
    private final StudentAccessService access = new StudentAccessService();
    public List<StudentCourse> getMyCourses() throws SQLException {
        return access.read(new EnrollmentDAO()::findByStudent);
    }
}
