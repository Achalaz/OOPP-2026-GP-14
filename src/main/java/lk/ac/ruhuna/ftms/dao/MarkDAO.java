package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.AssessmentMark;
import lk.ac.ruhuna.ftms.model.Mark;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MarkDAO {

    /** Only marks whose enrollment and assessment belong to this lecturer's assigned offerings. */
    public List<Mark> findByLecturer(int lecturerId) throws SQLException {
        String sql = """
                SELECT DISTINCT m.mark_id, m.enrollment_id, m.assessment_id, m.marks_obtained, m.mark_status, e.attempt_no
                FROM marks m
                JOIN enrollments e ON e.enrollment_id=m.enrollment_id
                JOIN assessments a ON a.assessment_id=m.assessment_id AND a.offering_id=e.offering_id
                JOIN lecturer_course_assignments l ON l.offering_id=e.offering_id
                JOIN users u ON u.user_id=l.lecturer_id
                WHERE l.lecturer_id=? AND u.role='LECTURER' AND u.is_active=TRUE
                  AND e.status<>'WITHDRAWN'
                ORDER BY m.mark_id
                """;
        List<Mark> marks = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, lecturerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Mark mark = new Mark(rs.getInt("mark_id"), rs.getInt("enrollment_id"),
                            rs.getInt("assessment_id"), rs.getObject("marks_obtained") == null
                                    ? null : rs.getDouble("marks_obtained"), rs.getInt("attempt_no"));
                    mark.setMarkStatus(rs.getString("mark_status"));
                    marks.add(mark);
                }
            }
        }
        return marks;
    }

    /** Lock the authorization and assessment rows with the write to prevent stale assignment/maxima checks. */
    public void saveLecturerMark(Mark mark, lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity identity)
            throws SQLException {
        mark.validateState();
        try (Connection con = DBConnection.getConnection()) {
            con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            con.setAutoCommit(false);
            try {
                var access = new AcademicAccessDAO();
                int offering = access.enrollment(con, mark.getEnrollmentId());
                access.offering(con, identity, offering);
                access.unpublished(con, mark.getEnrollmentId());
                String permission = """
                    SELECT a.max_mark FROM enrollments e
                    JOIN assessments a ON a.offering_id=e.offering_id
                    JOIN lecturer_course_assignments l ON l.offering_id=e.offering_id
                    JOIN users u ON u.user_id=l.lecturer_id
                    WHERE e.enrollment_id=? AND a.assessment_id=? AND e.attempt_no=?
                      AND e.status<>'WITHDRAWN' AND l.lecturer_id=?
                      AND u.role='LECTURER' AND u.is_active=TRUE FOR UPDATE
                    """;
                try (PreparedStatement ps = con.prepareStatement(permission)) {
                    ps.setInt(1, mark.getEnrollmentId()); ps.setInt(2, mark.getAssessmentId());
                    ps.setInt(3, mark.getAttemptNo()); ps.setInt(4, identity.userId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SecurityException("Course assignment or enrollment is no longer available.");
                        Double value = mark.getMarksObtained();
                        double max = rs.getDouble(1);
                        if (max <= 0 || (value != null && (!Double.isFinite(value) || value < 0 || value > max)))
                            throw new IllegalArgumentException("Mark must be between zero and the assessment maximum.");
                    }
                }
                lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);
                try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO marks(enrollment_id,assessment_id,marks_obtained,mark_status,entered_by)
                    VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE marks_obtained=VALUES(marks_obtained),
                    mark_status=VALUES(mark_status),entered_by=VALUES(entered_by)
                    """)) {
                    ps.setInt(1, mark.getEnrollmentId()); ps.setInt(2, mark.getAssessmentId());
                    if (mark.getMarksObtained() == null) ps.setNull(3, Types.DECIMAL);
                    else ps.setDouble(3, mark.getMarksObtained());
                    ps.setString(4, mark.getMarkStatus());
                    ps.setInt(5, identity.userId()); ps.executeUpdate();
                }
                access.invalidate(con, mark.getEnrollmentId());
                lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback(); throw error;
            }
        }
    }


    // SAVE OR UPDATE MARK
    // ---------------------------------------------------------
    public void saveOrUpdate(Mark mark) throws SQLException {
        saveLecturerMark(mark, lk.ac.ruhuna.ftms.util.SessionManager.requireSession());
    }
    // ---------------------------------------------------------
    // GET ALL MARKS
    // ---------------------------------------------------------
    public List<Mark> findAll() throws SQLException {

        List<Mark> marks = new ArrayList<>();

        String sql = """
                SELECT
                    m.mark_id,
                    m.enrollment_id,
                    m.assessment_id,
                    m.marks_obtained,
                    m.mark_status,
                    e.attempt_no
                FROM marks m
                JOIN enrollments e
                    ON m.enrollment_id = e.enrollment_id
                ORDER BY m.mark_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Double value =
                        rs.getObject("marks_obtained") == null
                                ? null
                                : rs.getDouble("marks_obtained");

                Mark mark = new Mark(
                        rs.getInt("mark_id"),
                        rs.getInt("enrollment_id"),
                        rs.getInt("assessment_id"),
                        value,
                        rs.getInt("attempt_no")
                );

                marks.add(mark);
                mark.setMarkStatus(rs.getString("mark_status"));
            }
        }

        return marks;
    }

    // ---------------------------------------------------------
    // GET CA MARKS FOR ONE ENROLLMENT
    // FINAL_EXAM excluded
    // ---------------------------------------------------------
    public List<AssessmentMark> findCAMarksByEnrollment(
            int enrollmentId) throws SQLException {

        List<AssessmentMark> marks = new ArrayList<>();

        String sql = """
                SELECT
                    m.marks_obtained,
                    a.max_mark,
                    a.weight_percentage
                FROM marks m
                JOIN assessments a
                    ON m.assessment_id = a.assessment_id
                WHERE m.enrollment_id = ?
                  AND a.assessment_type <> 'FINAL_EXAM'
                  AND m.mark_status = 'PRESENT'
                ORDER BY a.assessment_id
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, enrollmentId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    Double value =
                            rs.getObject("marks_obtained") == null
                                    ? null
                                    : rs.getDouble("marks_obtained");

                    AssessmentMark assessmentMark =
                            new AssessmentMark(
                                    value,
                                    rs.getDouble("max_mark"),
                                    rs.getDouble("weight_percentage")
                            );

                    marks.add(assessmentMark);
                }
            }
        }

        return marks;
    }

    // ---------------------------------------------------------
    // DELETE MARK
    // ---------------------------------------------------------
    public void delete(int markId) throws SQLException {
        var identity=lk.ac.ruhuna.ftms.util.SessionManager.requireSession();
        try(var c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                var access=new AcademicAccessDAO();
                if(!"ADMIN".equals(access.actor(c,identity).getRole()))throw new SecurityException("Only the existing administrator delete operation is supported.");
                int enrollment;
                try(var p=c.prepareStatement("SELECT enrollment_id FROM marks WHERE mark_id=? FOR UPDATE")){
                    p.setInt(1,markId);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Mark is unavailable.");enrollment=r.getInt(1);}
                }
                access.enrollment(c,enrollment);access.unpublished(c,enrollment);
                try(var p=c.prepareStatement("DELETE FROM marks WHERE mark_id=?")){p.setInt(1,markId);p.executeUpdate();}
                access.invalidate(c,enrollment);lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
}