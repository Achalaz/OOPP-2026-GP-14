package lk.ac.ruhuna.ftms.dao;
import java.sql.*;
import java.util.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.EligibilityOffering;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment;
import lk.ac.ruhuna.ftms.model.LecturerCourseAssignment.AssignmentType;
import lk.ac.ruhuna.ftms.util.SessionManager;
public class LecturerCourseAssignmentDAO {
    public record LecturerOption(int lecturerId, String name, String staffNo) {
        @Override public String toString() {
            return name + (staffNo == null || staffNo.isBlank() ? "" : " (" + staffNo + ")");
        }
    }

    protected Connection openConnection() throws SQLException { return DBConnection.getConnection(); }

    public List<LecturerOption> getEligibleLecturers(SessionManager.SessionIdentity identity) throws SQLException {
        return adminTransaction(identity, c -> {
            var rows = new ArrayList<LecturerOption>();
            try (var p = c.prepareStatement("""
                    SELECT l.lecturer_id, CONCAT_WS(' ',u.first_name,u.last_name) AS lecturer_name, l.staff_no
                    FROM lecturers l JOIN users u ON u.user_id=l.lecturer_id
                    WHERE u.role='LECTURER' AND u.is_active=1
                    ORDER BY u.first_name,u.last_name,l.staff_no
                    """); var r = p.executeQuery()) {
                while (r.next()) rows.add(new LecturerOption(r.getInt(1), r.getString(2), r.getString(3)));
            }
            return rows;
        });
    }

    public List<LecturerCourseAssignment> getAssignmentsByOffering(int offeringId,
            SessionManager.SessionIdentity identity) throws SQLException {
        return adminTransaction(identity, c -> {
            requireOffering(c, offeringId, false);
            var rows = new ArrayList<LecturerCourseAssignment>();
            try (var p = c.prepareStatement("""
                    SELECT a.assignment_id,a.offering_id,a.lecturer_id,
                           CONCAT_WS(' ',u.first_name,u.last_name) AS lecturer_name,l.staff_no,a.assignment_type,
                           cr.course_code,o.offering_group,
                           CONCAT(COALESCE(NULLIF(s.semester_name,''),CONCAT('Semester ',s.semester_no)),
                                  ' - ',ay.academic_year_name) AS semester
                    FROM lecturer_course_assignments a JOIN lecturers l ON l.lecturer_id=a.lecturer_id
                    JOIN users u ON u.user_id=l.lecturer_id JOIN course_offerings o ON o.offering_id=a.offering_id
                    JOIN courses cr ON cr.course_id=o.course_id JOIN semesters s ON s.semester_id=o.semester_id
                    JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
                    WHERE a.offering_id=? ORDER BY lecturer_name,l.staff_no,a.assignment_id
                    """)) {
                p.setInt(1, offeringId);
                try (var r = p.executeQuery()) {
                    while (r.next()) rows.add(new LecturerCourseAssignment(r.getInt(1), r.getInt(2), r.getInt(3),
                            r.getString(4), r.getString(5), AssignmentType.valueOf(r.getString(6)),
                            r.getString(7), r.getString(8), r.getString(9)));
                }
            }
            return rows;
        });
    }

    public int assignLecturer(int offeringId, int lecturerId, AssignmentType type,
            SessionManager.SessionIdentity identity) throws SQLException {
        return adminTransaction(identity, c -> {
            requireType(type);
            requireOffering(c, offeringId, true);
            requireEligibleLecturer(c, lecturerId);
            // The live unique key includes type. Serialize on the offering and enforce
            // the stricter Admin rule: one assignment per offering/lecturer pair.
            try (var p = c.prepareStatement("SELECT 1 FROM lecturer_course_assignments WHERE offering_id=? AND lecturer_id=?")) {
                p.setInt(1, offeringId); p.setInt(2, lecturerId);
                try (var r = p.executeQuery()) { if (r.next()) throw duplicateAssignment(); }
            }
            try (var p = c.prepareStatement("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1, offeringId); p.setInt(2, lecturerId); p.setString(3, type.name());
                p.executeUpdate();
                try (var keys = p.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Assignment ID was not returned.");
                    return keys.getInt(1);
                }
            }
        });
    }

    public void updateAssignmentType(int offeringId, int assignmentId, AssignmentType type,
            SessionManager.SessionIdentity identity) throws SQLException {
        adminTransaction(identity, c -> {
            requireType(type);
            int lecturerId = lockAssignment(c, offeringId, assignmentId);
            requireOffering(c, offeringId, true);
            requireEligibleLecturer(c, lecturerId);
            requireChangeAllowed(c, offeringId, lecturerId);
            try (var p = c.prepareStatement("SELECT 1 FROM lecturer_course_assignments WHERE offering_id=? AND lecturer_id=? AND assignment_id<>?")) {
                p.setInt(1, offeringId); p.setInt(2, lecturerId); p.setInt(3, assignmentId);
                try (var r = p.executeQuery()) { if (r.next()) throw duplicateAssignment(); }
            }
            try (var p = c.prepareStatement("UPDATE lecturer_course_assignments SET assignment_type=? WHERE assignment_id=? AND offering_id=?")) {
                p.setString(1, type.name()); p.setInt(2, assignmentId); p.setInt(3, offeringId); p.executeUpdate();
            }
            return null;
        });
    }

    public void removeAssignment(int offeringId, int assignmentId, SessionManager.SessionIdentity identity) throws SQLException {
        adminTransaction(identity, c -> {
            int lecturerId = lockAssignment(c, offeringId, assignmentId);
            requireOffering(c, offeringId, true);
            requireChangeAllowed(c, offeringId, lecturerId);
            try (var p = c.prepareStatement("DELETE FROM lecturer_course_assignments WHERE assignment_id=? AND offering_id=?")) {
                p.setInt(1, assignmentId); p.setInt(2, offeringId); p.executeUpdate();
            }
            return null;
        });
    }

    public String getAssignmentChangeBlocker(int offeringId, int assignmentId,
            SessionManager.SessionIdentity identity) throws SQLException {
        return adminTransaction(identity, c -> {
            int lecturerId = lockAssignment(c, offeringId, assignmentId);
            requireOffering(c, offeringId, true);
            return changeBlocker(c, offeringId, lecturerId);
        });
    }

    private int lockAssignment(Connection c, int offeringId, int assignmentId) throws SQLException {
        try (var p = c.prepareStatement("SELECT lecturer_id FROM lecturer_course_assignments WHERE assignment_id=? AND offering_id=? FOR UPDATE")) {
            p.setInt(1, assignmentId); p.setInt(2, offeringId);
            try (var r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("The selected assignment is no longer available. Refresh assignments.");
                return r.getInt(1);
            }
        }
    }

    private void requireOffering(Connection c, int offeringId, boolean modifying) throws SQLException {
        try (var p = c.prepareStatement("SELECT status FROM course_offerings WHERE offering_id=? FOR UPDATE")) {
            p.setInt(1, offeringId);
            try (var r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("The offering no longer exists. Refresh offerings.");
                if (modifying && "CANCELLED".equals(r.getString(1))) {
                    throw new IllegalStateException("Assignments for a cancelled offering are retained and cannot be changed.");
                }
            }
        }
    }

    private void requireEligibleLecturer(Connection c, int lecturerId) throws SQLException {
        try (var p = c.prepareStatement("""
                SELECT l.lecturer_id FROM lecturers l JOIN users u ON u.user_id=l.lecturer_id
                WHERE l.lecturer_id=? AND u.role='LECTURER' AND u.is_active=1 FOR SHARE
                """)) {
            p.setInt(1, lecturerId);
            try (var r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Select an active Lecturer with an existing lecturer profile. Refresh lecturers.");
            }
        }
    }

    private void requireChangeAllowed(Connection c, int offeringId, int lecturerId) throws SQLException {
        String blocker = changeBlocker(c, offeringId, lecturerId);
        if (blocker != null) throw new IllegalStateException(blocker);
    }

    private String changeBlocker(Connection c, int offeringId, int lecturerId) throws SQLException {
        // Academic rows do not reference assignment_id, and results have no publisher
        // assignment. Retain assignments once offering-level assessment/result history
        // exists, plus records attributed to this lecturer. Never rewrite that history.
        var checks = new LinkedHashMap<String, String>();
        checks.put("assessments", "SELECT 1 FROM assessments WHERE offering_id=? LIMIT 1");
        checks.put("marks", "SELECT 1 FROM marks m JOIN enrollments e ON e.enrollment_id=m.enrollment_id WHERE e.offering_id=? LIMIT 1");
        checks.put("results (including published results)", "SELECT 1 FROM course_results r JOIN enrollments e ON e.enrollment_id=r.enrollment_id WHERE e.offering_id=? LIMIT 1");
        checks.put("eligibility records", "SELECT 1 FROM eligibility_records r JOIN enrollments e ON e.enrollment_id=r.enrollment_id WHERE e.offering_id=? LIMIT 1");
        checks.put("course materials", "SELECT 1 FROM course_materials WHERE offering_id=? AND uploaded_by=? LIMIT 1");
        checks.put("attendance sessions", "SELECT 1 FROM attendance_sessions WHERE offering_id=? AND conducted_by=? LIMIT 1");
        checks.put("timetable entries", "SELECT 1 FROM timetable_entries WHERE offering_id=? AND lecturer_id=? LIMIT 1");
        checks.put("medical reviews", "SELECT 1 FROM medicals WHERE offering_id=? AND reviewed_by=? LIMIT 1");
        for (var entry : checks.entrySet()) {
            try (var p = c.prepareStatement(entry.getValue())) {
                p.setInt(1, offeringId);
                if (p.getParameterMetaData().getParameterCount() == 2) p.setInt(2, lecturerId);
                try (var r = p.executeQuery()) {
                    if (r.next()) return "Assignment retained: this offering has " + entry.getKey()
                            + ". Removal or type changes require an approved historical-assignment policy.";
                }
            }
        }
        return null;
    }

    private static void requireType(AssignmentType type) {
        if (type == null) throw new IllegalArgumentException("Select PRIMARY, ASSISTANT or PRACTICAL.");
    }
    private static SQLException duplicateAssignment() {
        return new SQLIntegrityConstraintViolationException("This lecturer is already assigned to the offering. Select the assignment to change its type.", "23000", 1062);
    }

    @FunctionalInterface private interface AdminWork<T> { T run(Connection c) throws SQLException; }
    private <T> T adminTransaction(SessionManager.SessionIdentity identity, AdminWork<T> work) throws SQLException {
        SessionManager.requireSession(identity);
        var user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || !"ADMIN".equals(user.getRole())) {
            throw new SecurityException("Only active Admin users can manage lecturer assignments.");
        }
        try (Connection c = openConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                if (!"ADMIN".equals(new AcademicAccessDAO().actor(c, identity).getRole())) {
                    throw new SecurityException("Active Admin access is required.");
                }
                T result = work.run(c);
                SessionManager.requireSession(identity);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { c.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }

    public int enrollmentOffering(int enrollment) throws SQLException {
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("SELECT offering_id FROM enrollments WHERE enrollment_id=?")) {
            p.setInt(1,enrollment);
            try(var r=p.executeQuery()){if(r.next())return r.getInt(1);}
        }
        throw new SecurityException("Enrollment is unavailable.");
    }
    public boolean isAssigned(int lecturer, int offering) throws SQLException {
        try(var c=DBConnection.getConnection(); var p=c.prepareStatement(
                "SELECT 1 FROM lecturer_course_assignments WHERE lecturer_id=? AND offering_id=? AND assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL')")) {
            p.setInt(1,lecturer); p.setInt(2,offering);
            try(var r=p.executeQuery()) { return r.next(); }
        }
    }
    public List<EligibilityOffering> findAssigned(int lecturer) throws SQLException {
        var rows=new ArrayList<EligibilityOffering>();
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("""
            SELECT DISTINCT o.offering_id,c.course_code,c.course_name,o.offering_group
            FROM lecturer_course_assignments a JOIN course_offerings o ON o.offering_id=a.offering_id
            JOIN courses c ON c.course_id=o.course_id WHERE a.lecturer_id=? AND o.status<>'CANCELLED' AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL') ORDER BY c.course_code,o.offering_id
            """)) {
            p.setInt(1,lecturer);
            try(var r=p.executeQuery()) { while(r.next()) rows.add(new EligibilityOffering(r.getInt(1),r.getString(2),r.getString(3),r.getString(4))); }
        }
        return rows;
    }
    public int enrollmentOffering(int enrollment, int assessment, int attempt) throws SQLException {
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("""
            SELECT e.offering_id FROM enrollments e JOIN assessments a ON a.offering_id=e.offering_id
            WHERE e.enrollment_id=? AND a.assessment_id=? AND e.attempt_no=? AND e.status<>'WITHDRAWN'
            """)) {
            p.setInt(1,enrollment);p.setInt(2,assessment);p.setInt(3,attempt);
            try(var r=p.executeQuery()) { if(r.next()) return r.getInt(1); }
        }
        throw new SecurityException("Enrollment, assessment and attempt must match an assigned course.");
    }

    // Keep the methods used by the newer CourseMaterials screen as well.
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

        try (Connection connection =
                     DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, lecturerUserId);
            statement.setInt(2, offeringId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

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

        try (Connection connection =
                     DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, lecturerUserId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    offeringIds.add(rs.getInt("offering_id"));
                }
            }
        }

        return offeringIds;
    }
}
