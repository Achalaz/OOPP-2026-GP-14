
package lk.ac.ruhuna.ftms.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only timetable database queries.
 * The Service layer verifies the logged-in user
 * and supplies the authorized department/student ID.
 */
public final class TimetableDAO {

    // Timetable data model
    public record TimetableEntry(
            int timetableId,
            int offeringId,
            String courseCode,
            String courseName,
            String offeringGroup,
            int semesterId,
            String dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String room,
            String sessionType,
            Integer lecturerId,
            String lecturerName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    // Common timetable query
    private static final String BASE_SELECT = """
            SELECT
                t.timetable_id,
                t.offering_id,
                c.course_code,
                c.course_name,
                o.offering_group,
                o.semester_id,
                t.day_of_week,
                t.start_time,
                t.end_time,
                t.room,
                t.session_type,
                t.lecturer_id,
                CONCAT_WS(
                    ' ',
                    u.first_name,
                    u.last_name
                ) AS lecturer_name,
                t.effective_from,
                t.effective_to

            FROM timetable_entries t

            JOIN course_offerings o
                ON o.offering_id = t.offering_id

            JOIN courses c
                ON c.course_id = o.course_id

            LEFT JOIN lecturers l
                ON l.lecturer_id = t.lecturer_id

            LEFT JOIN users u
                ON u.user_id = l.lecturer_id
            """;

    // Arrange entries Monday to Sunday
    private static final String SORT = """
            ORDER BY FIELD(
                t.day_of_week,
                'MONDAY',
                'TUESDAY',
                'WEDNESDAY',
                'THURSDAY',
                'FRIDAY',
                'SATURDAY',
                'SUNDAY'
            ),
            t.start_time,
            c.course_code,
            t.timetable_id
            """;

    /**
     * Get timetable for an authorized department.
     * Called by the Technical Officer Service.
     */
    public List<TimetableEntry> findByDepartment(
            Connection connection,
            int departmentId) throws SQLException {

        Objects.requireNonNull(
                connection,
                "Connection is required."
        );

        positive(departmentId, "Department ID");

        String sql = BASE_SELECT
                + " WHERE c.department_id = ? "
                + SORT;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, departmentId);

            return readRows(statement);
        }
    }

    /**
     * Get timetable of one course offering
     * belonging to an authorized department.
     */
    public List<TimetableEntry> findByOffering(
            Connection connection,
            int departmentId,
            int offeringId) throws SQLException {

        Objects.requireNonNull(
                connection,
                "Connection is required."
        );

        positive(departmentId, "Department ID");
        positive(offeringId, "Offering ID");

        String sql = BASE_SELECT
                + " WHERE c.department_id = ? "
                + " AND t.offering_id = ? "
                + SORT;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, departmentId);
            statement.setInt(2, offeringId);

            return readRows(statement);
        }
    }

    /**
     * Get timetable for an undergraduate.
     * Student ID must come from the login session.
     */
    public List<TimetableEntry> findForStudent(
            Connection connection,
            int studentId) throws SQLException {

        Objects.requireNonNull(
                connection,
                "Connection is required."
        );

        positive(studentId, "Student ID");

        String sql = BASE_SELECT + """
                WHERE EXISTS (
                    SELECT 1
                    FROM enrollments e

                    JOIN students s
                        ON s.student_id = e.student_id

                    WHERE e.offering_id = t.offering_id
                      AND s.student_id = ?
                      AND e.status IN (
                          'ENROLLED',
                          'REPEAT'
                      )
                )
                """ + SORT;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            return readRows(statement);
        }
    }

    // Convert database rows into timetable records
    private static List<TimetableEntry> readRows(
            PreparedStatement statement)
            throws SQLException {

        List<TimetableEntry> rows =
                new ArrayList<>();

        try (ResultSet rs =
                     statement.executeQuery()) {

            while (rs.next()) {

                Time start =
                        rs.getTime("start_time");

                Time end =
                        rs.getTime("end_time");

                Date from =
                        rs.getDate("effective_from");

                Date to =
                        rs.getDate("effective_to");

                Number lecturerNumber =
                        (Number) rs.getObject(
                                "lecturer_id"
                        );

                Integer lecturerId =
                        lecturerNumber == null
                                ? null
                                : lecturerNumber.intValue();

                rows.add(new TimetableEntry(
                        rs.getInt("timetable_id"),
                        rs.getInt("offering_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("offering_group"),
                        rs.getInt("semester_id"),
                        rs.getString("day_of_week"),

                        start == null
                                ? null
                                : start.toLocalTime(),

                        end == null
                                ? null
                                : end.toLocalTime(),

                        rs.getString("room"),
                        rs.getString("session_type"),
                        lecturerId,
                        rs.getString("lecturer_name"),

                        from == null
                                ? null
                                : from.toLocalDate(),

                        to == null
                                ? null
                                : to.toLocalDate()
                ));
            }
        }

        return List.copyOf(rows);
    }

    // Validate database IDs
    private static void positive(
            int id,
            String name) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive."
            );
        }
    }
}
