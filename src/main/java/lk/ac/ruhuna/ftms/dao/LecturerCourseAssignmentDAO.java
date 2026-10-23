
package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LecturerCourseAssignmentDAO {

    // Used by the admin assignment screen.
    public static final class LecturerOption {
        private final int lecturerId;
        private final String lecturerName;
        private final String staffNo;

        public LecturerOption(
                int lecturerId,
                String lecturerName,
                String staffNo) {
            this.lecturerId = lecturerId;
            this.lecturerName = lecturerName;
            this.staffNo = staffNo;
        }

        public int getLecturerId() {
            return lecturerId;
        }

        public String getLecturerName() {
            return lecturerName;
        }

        public String getStaffNo() {
            return staffNo;
        }

        @Override
        public String toString() {
            return lecturerName + " (" + staffNo + ")";
        }
    }

    // Verify that the same active admin session is still valid.
    private void requireAdmin(
            Connection connection,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        SessionManager.requireSession(identity);

        String sql = """
                SELECT 1
                FROM users
                WHERE user_id = ?
                  AND role = 'ADMIN'
                  AND is_active = 1
                LIMIT 1
                """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {
            ps.setInt(1, identity.userId());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SecurityException(
                            "Only active Admin users can manage assignments.");
                }
            }
        }
    }

    // Course Materials: check lecturer access to an offering.
    public boolean isAssignedToOffering(
            int lecturerUserId,
            int offeringId) throws SQLException {

        if (lecturerUserId <= 0 || offeringId <= 0) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM users u
                JOIN lecturers l
                  ON l.lecturer_id = u.user_id
                JOIN lecturer_course_assignments lca
                  ON lca.lecturer_id = l.lecturer_id
                WHERE u.user_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1
                  AND lca.offering_id = ?
                LIMIT 1
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, lecturerUserId);
            ps.setInt(2, offeringId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Course Materials: retrieve offerings assigned to a lecturer.
    public List<Integer> getAssignedOfferingIds(
            int lecturerUserId) throws SQLException {

        List<Integer> offeringIds = new ArrayList<>();

        if (lecturerUserId <= 0) {
            return offeringIds;
        }

        String sql = """
                SELECT DISTINCT lca.offering_id
                FROM users u
                JOIN lecturers l
                  ON l.lecturer_id = u.user_id
                JOIN lecturer_course_assignments lca
                  ON lca.lecturer_id = l.lecturer_id
                WHERE u.user_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1
                ORDER BY lca.offering_id DESC
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, lecturerUserId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    offeringIds.add(rs.getInt("offering_id"));
                }
            }
        }

        return offeringIds;
    }

    // Get active lecturers eligible for assignment.
    public List<LecturerOption> getEligibleLecturers(
            SessionManager.SessionIdentity identity)
            throws SQLException {

        List<LecturerOption> lecturers = new ArrayList<>();

        String sql = """
                SELECT l.lecturer_id,
                       CONCAT_WS(' ', u.first_name, u.last_name)
                           AS lecturer_name,
                       l.staff_no
                FROM lecturers l
                JOIN users u ON u.user_id = l.lecturer_id
                WHERE u.role = 'LECTURER'
                  AND u.is_active = 1
                ORDER BY u.first_name, u.last_name
                """;

        try (Connection connection = DBConnection.getConnection()) {
            requireAdmin(connection, identity);

            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    lecturers.add(new LecturerOption(
                            rs.getInt("lecturer_id"),
                            rs.getString("lecturer_name"),
                            rs.getString("staff_no")
                    ));
                }
            }
        }

        return lecturers;
    }

    // Retrieve assignments for an offering.
    public List<LecturerCourseAssignment> getAssignmentsByOffering(
            int offeringId,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        if (offeringId <= 0) {
            throw new IllegalArgumentException(
                    "Select an existing offering.");
        }

        List<LecturerCourseAssignment> assignments =
                new ArrayList<>();

        String sql = """
                SELECT lca.assignment_id,
                       lca.offering_id,
                       l.lecturer_id,
                       CONCAT_WS(' ', u.first_name, u.last_name)
                           AS lecturer_name,
                       l.staff_no,
                       lca.assignment_type,
                       c.course_code,
                       co.offering_group,
                       CAST(co.semester_id AS CHAR) AS semester
                FROM lecturer_course_assignments lca
                JOIN lecturers l
                  ON l.lecturer_id = lca.lecturer_id
                JOIN users u
                  ON u.user_id = l.lecturer_id
                JOIN course_offerings co
                  ON co.offering_id = lca.offering_id
                JOIN courses c
                  ON c.course_id = co.course_id
                WHERE lca.offering_id = ?
                ORDER BY u.first_name, u.last_name
                """;

        try (Connection connection = DBConnection.getConnection()) {
            requireAdmin(connection, identity);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, offeringId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assignments.add(
                                new LecturerCourseAssignment(
                                        rs.getInt("assignment_id"),
                                        rs.getInt("offering_id"),
                                        rs.getInt("lecturer_id"),
                                        rs.getString("lecturer_name"),
                                        rs.getString("staff_no"),
                                        AssignmentType.valueOf(
                                                rs.getString("assignment_type")),
                                        rs.getString("course_code"),
                                        rs.getString("offering_group"),
                                        rs.getString("semester")
                                )
                        );
                    }
                }
            }
        }

        return assignments;
    }

    private void lockOffering(
            Connection connection,
            int offeringId) throws SQLException {

        String sql = """
                SELECT offering_id
                FROM course_offerings
                WHERE offering_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offeringId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "The selected course offering does not exist.");
                }
            }
        }
    }

    private void requireActiveLecturer(
            Connection connection,
            int lecturerId) throws SQLException {

        String sql = """
                SELECT 1
                FROM lecturers l
                JOIN users u ON u.user_id = l.lecturer_id
                WHERE l.lecturer_id = ?
                  AND u.role = 'LECTURER'
                  AND u.is_active = 1
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, lecturerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Select an active lecturer.");
                }
            }
        }
    }

    // Assign a lecturer to an offering.
    // Returns the newly created assignment ID.
    public int assignLecturer(
            int offeringId,
            int lecturerId,
            AssignmentType type,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        if (offeringId <= 0 || lecturerId <= 0 || type == null) {
            throw new IllegalArgumentException(
                    "Select an offering, lecturer and assignment type.");
        }

        String duplicateSql = """
                SELECT 1
                FROM lecturer_course_assignments
                WHERE offering_id = ?
                  AND lecturer_id = ?
                LIMIT 1
                """;

        String insertSql = """
                INSERT INTO lecturer_course_assignments
                    (offering_id, lecturer_id, assignment_type)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                requireAdmin(connection, identity);
                lockOffering(connection, offeringId);
                requireActiveLecturer(connection, lecturerId);

                try (PreparedStatement ps =
                             connection.prepareStatement(duplicateSql)) {
                    ps.setInt(1, offeringId);
                    ps.setInt(2, lecturerId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException(
                                    "This lecturer is already assigned "
                                            + "to the selected offering.");
                        }
                    }
                }

                int assignmentId;

                try (PreparedStatement ps = connection.prepareStatement(
                        insertSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setInt(1, offeringId);
                    ps.setInt(2, lecturerId);
                    ps.setString(3, type.name());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not retrieve the assignment ID.");
                        }

                        assignmentId = keys.getInt(1);
                    }
                }

                connection.commit();
                return assignmentId;

            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    ex.addSuppressed(rollbackError);
                }
                throw ex;

            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closed after this method.
                }
            }
        }
    }

    private AssignmentType lockAssignment(
            Connection connection,
            int offeringId,
            int assignmentId) throws SQLException {

        String sql = """
                SELECT assignment_type
                FROM lecturer_course_assignments
                WHERE offering_id = ?
                  AND assignment_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offeringId);
            ps.setInt(2, assignmentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "The selected lecturer assignment was not found.");
                }

                return AssignmentType.valueOf(
                        rs.getString("assignment_type"));
            }
        }
    }

    private int countPrimaryAssignments(
            Connection connection,
            int offeringId) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM lecturer_course_assignments
                WHERE offering_id = ?
                  AND assignment_type = 'PRIMARY'
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offeringId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String findAssignmentChangeBlocker(
            Connection connection,
            int offeringId,
            int assignmentId) throws SQLException {

        AssignmentType currentType;

        String sql = """
                SELECT assignment_type
                FROM lecturer_course_assignments
                WHERE offering_id = ?
                  AND assignment_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offeringId);
            ps.setInt(2, assignmentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "The selected lecturer assignment no longer exists.";
                }

                currentType = AssignmentType.valueOf(
                        rs.getString("assignment_type"));
            }
        }

        if (currentType == AssignmentType.PRIMARY
                && countPrimaryAssignments(connection, offeringId) <= 1) {
            return "This is the only PRIMARY lecturer assigned to this offering.";
        }

        return null;
    }

    // Preview whether changing/removing an assignment is blocked.
    public String getAssignmentChangeBlocker(
            int offeringId,
            int assignmentId,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        if (offeringId <= 0 || assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "Select an offering and an assigned lecturer.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            requireAdmin(connection, identity);

            return findAssignmentChangeBlocker(
                    connection, offeringId, assignmentId);
        }
    }

    // Change PRIMARY / ASSISTANT / PRACTICAL.
    public void updateAssignmentType(
            int offeringId,
            int assignmentId,
            AssignmentType type,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        if (offeringId <= 0 || assignmentId <= 0 || type == null) {
            throw new IllegalArgumentException(
                    "Select an assignment and a valid type.");
        }

        String sql = """
                UPDATE lecturer_course_assignments
                SET assignment_type = ?
                WHERE offering_id = ?
                  AND assignment_id = ?
                """;

        try (Connection connection = DBConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                requireAdmin(connection, identity);
                lockOffering(connection, offeringId);

                AssignmentType currentType = lockAssignment(
                        connection, offeringId, assignmentId);

                if (currentType == type) {
                    connection.commit();
                    return;
                }

                String blocker = findAssignmentChangeBlocker(
                        connection, offeringId, assignmentId);

                if (blocker != null
                        && currentType == AssignmentType.PRIMARY
                        && type != AssignmentType.PRIMARY) {
                    throw new SQLException(blocker);
                }

                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, type.name());
                    ps.setInt(2, offeringId);
                    ps.setInt(3, assignmentId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Could not update the assignment.");
                    }
                }

                connection.commit();

            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    ex.addSuppressed(rollbackError);
                }
                throw ex;

            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closed after this method.
                }
            }
        }
    }

    // Remove an assignment.
    public void removeAssignment(
            int offeringId,
            int assignmentId,
            SessionManager.SessionIdentity identity)
            throws SQLException {

        if (offeringId <= 0 || assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "Select an offering and an assigned lecturer.");
        }

        String sql = """
                DELETE FROM lecturer_course_assignments
                WHERE offering_id = ?
                  AND assignment_id = ?
                """;

        try (Connection connection = DBConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                requireAdmin(connection, identity);
                lockOffering(connection, offeringId);
                lockAssignment(connection, offeringId, assignmentId);

                String blocker = findAssignmentChangeBlocker(
                        connection, offeringId, assignmentId);

                if (blocker != null) {
                    throw new SQLException(blocker);
                }

                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, offeringId);
                    ps.setInt(2, assignmentId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Could not remove the assignment.");
                    }
                }

                connection.commit();

            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    ex.addSuppressed(rollbackError);
                }
                throw ex;

            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closed after this method.
                }
            }
        }
    }
}
