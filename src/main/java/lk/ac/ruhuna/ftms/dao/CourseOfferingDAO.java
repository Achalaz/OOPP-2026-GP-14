
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseOffering;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseOfferingDAO {

    protected Connection openConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    public record CourseOption(
            int courseId,
            String courseCode,
            String courseName,
            double credits
    ) {
        @Override
        public String toString() {
            return courseCode + " - " + courseName
                    + " (" + credits + " credits)";
        }
    }

    public record SemesterOption(
            int semesterId,
            String displayName
    ) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final String SELECT_SQL = """
        SELECT
            co.offering_id,
            co.course_id,
            co.semester_id,
            co.offering_group,
            co.status,
            co.created_at,
            c.course_code,
            c.course_name,
            c.credits,
            COALESCE(
                NULLIF(s.semester_name, ''),
                CONCAT('Semester ', s.semester_no)
            ) AS display_semester_name,
            ay.academic_year_name
        FROM course_offerings co
        JOIN courses c
            ON c.course_id = co.course_id
        JOIN semesters s
            ON s.semester_id = co.semester_id
        JOIN academic_years ay
            ON ay.academic_year_id = s.academic_year_id
        """;

    // READ: Retrieve all course offerings
    /** Enrollment picker uses the caller's authorized transaction and the existing offering mapper. */
    public List<CourseOffering> findAvailableOfferings(Connection connection, String keyword) throws SQLException {
        String sql = SELECT_SQL + """
                WHERE co.status IN ('PLANNED','ACTIVE') AND
                CONCAT_WS(' ',co.offering_id,c.course_code,c.course_name,s.semester_name,
                          s.semester_no,ay.academic_year_name,co.offering_group,co.status) LIKE ?
                ORDER BY ay.academic_year_name DESC,s.semester_no,c.course_code,co.offering_group
                """;
        var offerings = new ArrayList<CourseOffering>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + java.util.Objects.toString(keyword, "").strip() + "%");
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) offerings.add(mapOffering(rows));
            }
        }
        return List.copyOf(offerings);
    }

    public List<CourseOffering> getAllOfferings()
            throws SQLException {

        String sql = SELECT_SQL
                + " ORDER BY ay.academic_year_name DESC, "
                + "s.semester_no, c.course_code, co.offering_group";

        List<CourseOffering> offerings = new ArrayList<>();

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                offerings.add(mapOffering(rs));
            }
        }

        return offerings;
    }

    // SEARCH: Search by course, semester, group or status
    public List<CourseOffering> searchOfferings(String keyword)
            throws SQLException {

        String sql = SELECT_SQL + """
            WHERE c.course_code LIKE ?
               OR c.course_name LIKE ?
               OR s.semester_name LIKE ?
               OR ay.academic_year_name LIKE ?
               OR co.offering_group LIKE ?
               OR co.status LIKE ?
            ORDER BY c.course_code, co.offering_group
            """;

        List<CourseOffering> offerings = new ArrayList<>();
        String value = "%" + keyword.trim() + "%";

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 1; i <= 6; i++) {
                stmt.setString(i, value);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    offerings.add(mapOffering(rs));
                }
            }
        }

        return offerings;
    }

    // CREATE or UPDATE
    public boolean saveOffering(CourseOffering offering)
            throws SQLException {

        if (offering == null) {
            throw new IllegalArgumentException(
                    "Course offering cannot be null."
            );
        }

        if (offering.getCourseId() <= 0
                || offering.getSemesterId() <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid course and semester."
            );
        }

        String group = offering.getOfferingGroup();

        if (group == null || group.isBlank()
                || group.trim().length() > 30) {
            throw new IllegalArgumentException(
                    "Enter a group name of 1-30 characters."
            );
        }

        String status = offering.getStatus();

        if (status == null || !List.of(
                "PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"
        ).contains(status)) {
            throw new IllegalArgumentException(
                    "Select a valid offering status."
            );
        }

        if (offering.getOfferingId() > 0) {
            return updateOffering(offering);
        }

        String sql = """
            INSERT INTO course_offerings
                (course_id, semester_id, offering_group, status)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, offering.getCourseId());
            stmt.setInt(2, offering.getSemesterId());
            stmt.setString(3, group.trim());
            stmt.setString(4, status);

            if (stmt.executeUpdate() == 0) {
                return false;
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    offering.setOfferingId(keys.getInt(1));
                }
            }

            return true;
        }
    }

    private boolean updateOffering(CourseOffering offering)
            throws SQLException {

        String sql = """
            UPDATE course_offerings
            SET course_id = ?,
                semester_id = ?,
                offering_group = ?,
                status = ?
            WHERE offering_id = ?
            """;

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, offering.getCourseId());
            stmt.setInt(2, offering.getSemesterId());
            stmt.setString(
                    3, offering.getOfferingGroup().trim()
            );
            stmt.setString(4, offering.getStatus());
            stmt.setInt(5, offering.getOfferingId());

            return stmt.executeUpdate() > 0;
        }
    }

    // DELETE
    public boolean deleteOffering(int offeringId)
            throws SQLException {

        if (offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid offering ID."
            );
        }

        String sql = """
            DELETE FROM course_offerings
            WHERE offering_id = ?
            """;

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock the parent before checking references. InnoDB FK checks then
                // prevent concurrent child inserts until this transaction completes.
                try (PreparedStatement lock = conn.prepareStatement(
                        "SELECT offering_id FROM course_offerings WHERE offering_id = ? FOR UPDATE")) {
                    lock.setInt(1, offeringId);
                    try (ResultSet row = lock.executeQuery()) {
                        if (!row.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                requireUnlinkedOffering(conn, offeringId);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, offeringId);
                    boolean deleted = stmt.executeUpdate() > 0;
                    conn.commit();
                    return deleted;
                }
            } catch (SQLException | RuntimeException error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }

    private void requireUnlinkedOffering(Connection conn, int offeringId) throws SQLException {
        // Verified against ftms_group14: these FKs use CASCADE or SET NULL.
        // Refuse deletion instead of allowing MySQL to remove/rewrite academic records.
        for (String table : List.of("assessments", "lecturer_course_assignments",
                "attendance_sessions", "enrollments", "course_materials", "medicals", "timetable_entries")) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM " + table + " WHERE offering_id = ? LIMIT 1")) {
                stmt.setInt(1, offeringId);
                try (ResultSet rows = stmt.executeQuery()) {
                    if (rows.next()) {
                        throw new SQLIntegrityConstraintViolationException(
                                "This offering is linked to academic records and cannot be deleted.", "23000", 1451);
                    }
                }
            }
        }
    }

    // Dropdown options: active courses only
    public List<CourseOption> getActiveCourses()
            throws SQLException {

        String sql = """
            SELECT course_id, course_code, course_name, credits
            FROM courses
            WHERE is_active = TRUE
            ORDER BY course_code
            """;

        List<CourseOption> courses = new ArrayList<>();

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                courses.add(new CourseOption(
                        rs.getInt("course_id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getDouble("credits")
                ));
            }
        }

        return courses;
    }

    // Dropdown options: semesters and academic years
    public List<SemesterOption> getSemesters()
            throws SQLException {

        String sql = """
            SELECT
                s.semester_id,
                COALESCE(
                    NULLIF(s.semester_name, ''),
                    CONCAT('Semester ', s.semester_no)
                ) AS display_name,
                ay.academic_year_name
            FROM semesters s
            JOIN academic_years ay
                ON ay.academic_year_id = s.academic_year_id
            ORDER BY ay.academic_year_name DESC, s.semester_no
            """;

        List<SemesterOption> semesters = new ArrayList<>();

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                semesters.add(new SemesterOption(
                        rs.getInt("semester_id"),
                        rs.getString("display_name")
                                + " - "
                                + rs.getString("academic_year_name")
                ));
            }
        }

        return semesters;
    }

    // Convert a database row into a CourseOffering object
    private CourseOffering mapOffering(ResultSet rs)
            throws SQLException {

        CourseOffering offering = new CourseOffering();

        offering.setOfferingId(rs.getInt("offering_id"));
        offering.setCourseId(rs.getInt("course_id"));
        offering.setSemesterId(rs.getInt("semester_id"));
        offering.setOfferingGroup(
                rs.getString("offering_group")
        );
        offering.setStatus(rs.getString("status"));

        Timestamp createdAt = rs.getTimestamp("created_at");

        if (createdAt != null) {
            offering.setCreatedAt(createdAt.toLocalDateTime());
        }

        offering.setCourseCode(rs.getString("course_code"));
        offering.setCourseName(rs.getString("course_name"));
        offering.setCredits(rs.getDouble("credits"));
        offering.setSemesterName(
                rs.getString("display_semester_name")
        );
        offering.setAcademicYearName(
                rs.getString("academic_year_name")
        );

        return offering;
    }
}
