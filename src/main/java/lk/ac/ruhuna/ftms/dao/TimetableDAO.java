
package lk.ac.ruhuna.ftms.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Department-scoped timetable database operations.
 *
 * The Service layer must:
 * 1. Verify the Technical Officer session.
 * 2. Find the officer's authorized department.
 * 3. Manage the database transaction.
 */
public final class TimetableDAO {

    // Timetable display model

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

    // Data for adding and updating timetable entries

    public record TimetableInput(
            int offeringId,
            String dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String room,
            String sessionType,
            Integer lecturerId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    private static final Set<String> DAYS = Set.of(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY"
    );

    private static final Set<String> SESSION_TYPES = Set.of(
            "LECTURE",
            "PRACTICAL",
            "TUTORIAL",
            "OTHER"
    );

    // Common timetable SELECT statement

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

    // Sort Monday to Sunday

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

    // ==================================================
    // READ OPERATIONS - EXISTING FUNCTIONALITY
    // ==================================================

    // Get department timetable

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

    // Get timetable for one course offering

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

    // Get timetable for one enrolled student

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

    // ==================================================
    // CREATE TIMETABLE ENTRY
    // ==================================================

    public int createEntry(
            Connection connection,
            int departmentId,
            TimetableInput input) throws SQLException {

        requireWriteTransaction(connection);

        positive(departmentId, "Department ID");

        TimetableInput value = validate(input);

        // Check authorized department
        requireOffering(
                connection,
                departmentId,
                value.offeringId()
        );

        // Check lecturer assignment
        requireAssignedLecturer(connection, value);

        // Check timetable conflicts
        rejectConflicts(connection, value, 0);

        String sql = """
                INSERT INTO timetable_entries
                    (
                        offering_id,
                        day_of_week,
                        start_time,
                        end_time,
                        room,
                        session_type,
                        lecturer_id,
                        effective_from,
                        effective_to
                    )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             PreparedStatement.RETURN_GENERATED_KEYS
                     )) {

            bindInput(statement, value);

            if (statement.executeUpdate() != 1) {

                throw new SQLException(
                        "Timetable entry could not be created."
                );
            }

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {

                    throw new SQLException(
                            "Missing generated timetable ID."
                    );
                }

                return keys.getInt(1);
            }
        }
    }

    // ==================================================
    // UPDATE TIMETABLE ENTRY
    // ==================================================

    public void updateEntry(
            Connection connection,
            int departmentId,
            int timetableId,
            TimetableInput input) throws SQLException {

        requireWriteTransaction(connection);

        positive(departmentId, "Department ID");
        positive(timetableId, "Timetable ID");

        TimetableInput value = validate(input);

        // Verify this entry belongs to the department
        int originalOffering = requireOwnedEntry(
                connection,
                departmentId,
                timetableId
        );

        // Verify the replacement course is authorized
        requireOffering(
                connection,
                departmentId,
                value.offeringId()
        );

        requireAssignedLecturer(connection, value);

        // Ignore this entry when checking conflicts
        rejectConflicts(
                connection,
                value,
                timetableId
        );

        String sql = """
                UPDATE timetable_entries

                SET offering_id = ?,
                    day_of_week = ?,
                    start_time = ?,
                    end_time = ?,
                    room = ?,
                    session_type = ?,
                    lecturer_id = ?,
                    effective_from = ?,
                    effective_to = ?

                WHERE timetable_id = ?
                  AND offering_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            bindInput(statement, value);

            statement.setInt(10, timetableId);
            statement.setInt(11, originalOffering);

            // Zero changed rows can mean unchanged values.
            statement.executeUpdate();
        }
    }

    // ==================================================
    // DELETE TIMETABLE ENTRY
    // ==================================================

    public void deleteEntry(
            Connection connection,
            int departmentId,
            int timetableId) throws SQLException {

        requireWriteTransaction(connection);

        positive(departmentId, "Department ID");
        positive(timetableId, "Timetable ID");

        // Verify entry belongs to this department
        int originalOffering = requireOwnedEntry(
                connection,
                departmentId,
                timetableId
        );

        String sql = """
                DELETE FROM timetable_entries
                WHERE timetable_id = ?
                  AND offering_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, timetableId);
            statement.setInt(2, originalOffering);

            if (statement.executeUpdate() != 1) {

                throw new SQLException(
                        "Timetable entry was not deleted."
                );
            }
        }
    }

    // ==================================================
    // VALIDATION
    // ==================================================

    private static void requireWriteTransaction(
            Connection connection) throws SQLException {

        Objects.requireNonNull(
                connection,
                "Connection is required."
        );

        if (connection.getAutoCommit()) {

            throw new IllegalStateException(
                    "Timetable writes require "
                            + "a Service-managed transaction."
            );
        }
    }

    private static TimetableInput validate(
            TimetableInput input) {

        Objects.requireNonNull(
                input,
                "Timetable values are required."
        );

        positive(input.offeringId(), "Offering ID");

        String day = normalized(
                input.dayOfWeek()
        );

        String type = normalized(
                input.sessionType()
        );

        if (!DAYS.contains(day)) {

            throw new IllegalArgumentException(
                    "Choose a valid day of week."
            );
        }

        if (!SESSION_TYPES.contains(type)) {

            throw new IllegalArgumentException(
                    "Choose a valid session type."
            );
        }

        // Validate time range

        if (input.startTime() == null
                || input.endTime() == null
                || !input.startTime().isBefore(
                input.endTime()
        )) {

            throw new IllegalArgumentException(
                    "Start time must be earlier than end time."
            );
        }

        // Validate effective dates

        if (input.effectiveFrom() != null
                && input.effectiveTo() != null
                && input.effectiveFrom().isAfter(
                input.effectiveTo()
        )) {

            throw new IllegalArgumentException(
                    "Effective-from date must not "
                            + "be after effective-to date."
            );
        }

        // Lecturer is optional

        if (input.lecturerId() != null) {

            positive(
                    input.lecturerId(),
                    "Lecturer ID"
            );
        }

        // Room is optional

        String room = input.room() == null
                ? null
                : input.room().strip();

        if (room != null && room.isBlank()) {
            room = null;
        }

        if (room != null && room.length() > 80) {

            throw new IllegalArgumentException(
                    "Room must be at most 80 characters."
            );
        }

        return new TimetableInput(
                input.offeringId(),
                day,
                input.startTime(),
                input.endTime(),
                room,
                type,
                input.lecturerId(),
                input.effectiveFrom(),
                input.effectiveTo()
        );
    }

    private static String normalized(String text) {

        return text == null
                ? ""
                : text.strip().toUpperCase(Locale.ROOT);
    }

    // ==================================================
    // DEPARTMENT AUTHORIZATION
    // ==================================================

    private static void requireOffering(
            Connection connection,
            int departmentId,
            int offeringId) throws SQLException {

        String sql = """
                SELECT 1

                FROM course_offerings o

                JOIN courses c
                    ON c.course_id = o.course_id

                WHERE o.offering_id = ?
                  AND c.department_id = ?

                FOR SHARE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, offeringId);
            statement.setInt(2, departmentId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new SecurityException(
                            "This offering is outside "
                                    + "your authorized department."
                    );
                }
            }
        }
    }

    // Verify and lock the existing timetable row

    private static int requireOwnedEntry(
            Connection connection,
            int departmentId,
            int timetableId) throws SQLException {

        String sql = """
                SELECT t.offering_id

                FROM timetable_entries t

                JOIN course_offerings o
                    ON o.offering_id = t.offering_id

                JOIN courses c
                    ON c.course_id = o.course_id

                WHERE t.timetable_id = ?
                  AND c.department_id = ?

                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, timetableId);
            statement.setInt(2, departmentId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new SecurityException(
                            "Timetable entry is unavailable "
                                    + "in your department."
                    );
                }

                return rs.getInt(1);
            }
        }
    }

    // ==================================================
    // LECTURER ASSIGNMENT VALIDATION
    // ==================================================

    private static void requireAssignedLecturer(
            Connection connection,
            TimetableInput value) throws SQLException {

        if (value.lecturerId() == null) {
            return;
        }

        String sql = """
                SELECT 1

                FROM lecturer_course_assignments a

                JOIN lecturers l
                    ON l.lecturer_id = a.lecturer_id

                JOIN users u
                    ON u.user_id = l.lecturer_id

                WHERE a.offering_id = ?
                  AND a.lecturer_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1

                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    value.offeringId()
            );

            statement.setInt(
                    2,
                    value.lecturerId()
            );

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (!rs.next()) {

                    throw new IllegalArgumentException(
                            "Select an active lecturer "
                                    + "assigned to this course offering."
                    );
                }
            }
        }
    }

    // ==================================================
    // TIME CONFLICT VALIDATION
    // ==================================================

    private static void rejectConflicts(
            Connection connection,
            TimetableInput value,
            int excludeId) throws SQLException {

        String sql = """
                SELECT t.timetable_id

                FROM timetable_entries t

                WHERE t.day_of_week = ?

                  AND t.start_time < ?
                  AND t.end_time > ?

                  AND (
                      t.effective_to IS NULL
                      OR ? IS NULL
                      OR t.effective_to >= ?
                  )

                  AND (
                      t.effective_from IS NULL
                      OR ? IS NULL
                      OR t.effective_from <= ?
                  )

                  AND t.timetable_id <> ?

                  AND (
                      t.offering_id = ?

                      OR (
                          ? IS NOT NULL
                          AND t.room IS NOT NULL
                          AND LOWER(TRIM(t.room)) = LOWER(?)
                      )

                      OR (
                          ? IS NOT NULL
                          AND t.lecturer_id = ?
                      )
                  )

                LIMIT 1

                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    value.dayOfWeek()
            );

            statement.setTime(
                    2,
                    Time.valueOf(value.endTime())
            );

            statement.setTime(
                    3,
                    Time.valueOf(value.startTime())
            );

            setNullableDate(
                    statement,
                    4,
                    value.effectiveFrom()
            );

            setNullableDate(
                    statement,
                    5,
                    value.effectiveFrom()
            );

            setNullableDate(
                    statement,
                    6,
                    value.effectiveTo()
            );

            setNullableDate(
                    statement,
                    7,
                    value.effectiveTo()
            );

            statement.setInt(8, excludeId);
            statement.setInt(9, value.offeringId());

            statement.setString(10, value.room());
            statement.setString(11, value.room());

            setNullableInt(
                    statement,
                    12,
                    value.lecturerId()
            );

            setNullableInt(
                    statement,
                    13,
                    value.lecturerId()
            );

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (rs.next()) {

                    throw new IllegalArgumentException(
                            "Schedule conflict with timetable ID "
                                    + rs.getInt(1)
                                    + ": overlapping course, "
                                    + "room or lecturer."
                    );
                }
            }
        }
    }

    // ==================================================
    // DATABASE PARAMETER HELPERS
    // ==================================================

    private static void bindInput(
            PreparedStatement statement,
            TimetableInput value) throws SQLException {

        statement.setInt(
                1,
                value.offeringId()
        );

        statement.setString(
                2,
                value.dayOfWeek()
        );

        statement.setTime(
                3,
                Time.valueOf(value.startTime())
        );

        statement.setTime(
                4,
                Time.valueOf(value.endTime())
        );

        statement.setString(
                5,
                value.room()
        );

        statement.setString(
                6,
                value.sessionType()
        );

        setNullableInt(
                statement,
                7,
                value.lecturerId()
        );

        setNullableDate(
                statement,
                8,
                value.effectiveFrom()
        );

        setNullableDate(
                statement,
                9,
                value.effectiveTo()
        );
    }

    private static void setNullableDate(
            PreparedStatement statement,
            int index,
            LocalDate value) throws SQLException {

        if (value == null) {

            statement.setNull(
                    index,
                    Types.DATE
            );

        } else {

            statement.setDate(
                    index,
                    Date.valueOf(value)
            );
        }
    }

    private static void setNullableInt(
            PreparedStatement statement,
            int index,
            Integer value) throws SQLException {

        if (value == null) {

            statement.setNull(
                    index,
                    Types.INTEGER
            );

        } else {

            statement.setInt(
                    index,
                    value
            );
        }
    }

    // ==================================================
    // RESULT SET MAPPING - EXISTING FUNCTIONALITY
    // ==================================================

    private static List<TimetableEntry> readRows(
            PreparedStatement statement) throws SQLException {

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

                Number lecturer =
                        (Number) rs.getObject(
                                "lecturer_id"
                        );

                rows.add(
                        new TimetableEntry(
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

                                lecturer == null
                                        ? null
                                        : lecturer.intValue(),

                                rs.getString("lecturer_name"),

                                from == null
                                        ? null
                                        : from.toLocalDate(),

                                to == null
                                        ? null
                                        : to.toLocalDate()
                        )
                );
            }
        }

        return List.copyOf(rows);
    }

    // Validate positive IDs

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
