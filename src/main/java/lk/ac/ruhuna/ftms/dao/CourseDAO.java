package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.model.Course;
import lk.ac.ruhuna.ftms.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {

    private static final String SELECT_SQL =
            "SELECT course_id, course_code, course_name, credits, " +
                    "department_id, course_type, is_active, created_at " +
                    "FROM courses ";

    public List<Course> findAll() throws SQLException {
        String sql = SELECT_SQL + "ORDER BY course_id DESC";

        List<Course> courses = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                courses.add(mapRow(resultSet));
            }
        }

        return courses;
    }

    public List<Course> search(String keyword) throws SQLException {
        String sql = SELECT_SQL +
                "WHERE course_code LIKE ? OR course_name LIKE ? " +
                "ORDER BY course_id DESC";

        List<Course> courses = new ArrayList<>();
        String searchValue = "%" + keyword.trim() + "%";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, searchValue);
            statement.setString(2, searchValue);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    courses.add(mapRow(resultSet));
                }
            }
        }

        return courses;
    }

    public int insert(Course course) throws SQLException {
        String sql =
                "INSERT INTO courses " +
                        "(course_code, course_name, credits, department_id, " +
                        "course_type, is_active) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, course.getCourseCode());
            statement.setString(2, course.getCourseName());
            statement.setBigDecimal(3, course.getCredits());
            statement.setInt(4, course.getDepartmentId());
            statement.setString(5, course.getCourseType());
            statement.setBoolean(6, course.isActive());

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating course failed.");
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    course.setCourseId(generatedId);
                    return generatedId;
                }
            }
        }

        throw new SQLException("Course ID was not generated.");
    }

    public boolean update(Course course) throws SQLException {
        String sql =
                "UPDATE courses SET course_code = ?, course_name = ?, " +
                        "credits = ?, department_id = ?, course_type = ?, " +
                        "is_active = ? WHERE course_id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, course.getCourseCode());
            statement.setString(2, course.getCourseName());
            statement.setBigDecimal(3, course.getCredits());
            statement.setInt(4, course.getDepartmentId());
            statement.setString(5, course.getCourseType());
            statement.setBoolean(6, course.isActive());
            statement.setInt(7, course.getCourseId());

            return statement.executeUpdate() > 0;
        }
    }

    public boolean delete(int courseId) throws SQLException {
        String sql = "DELETE FROM courses WHERE course_id = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            return statement.executeUpdate() > 0;
        }
    }

    private Course mapRow(ResultSet rs) throws SQLException {
        Course course = new Course();

        course.setCourseId(rs.getInt("course_id"));
        course.setCourseCode(rs.getString("course_code"));
        course.setCourseName(rs.getString("course_name"));
        course.setCredits(rs.getBigDecimal("credits"));
        course.setDepartmentId(rs.getInt("department_id"));
        course.setCourseType(rs.getString("course_type"));
        course.setActive(rs.getBoolean("is_active"));

        Timestamp timestamp = rs.getTimestamp("created_at");

        if (timestamp != null) {
            course.setCreatedAt(timestamp.toLocalDateTime());
        }

        return course;
    }
}