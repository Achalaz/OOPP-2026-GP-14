package lk.ac.ruhuna.ftms.dao;

import java.sql.*;
import lk.ac.ruhuna.ftms.exception.PublishedResultProtectedException;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

/** Shared transaction guards for academic services. No caller-supplied actor is trusted. */
public class AcademicAccessDAO {
    public record OfferingGap(int offeringId, String courseCode, String semester, String group) {
        @Override public String toString() {
            return "Offering " + offeringId + " | " + courseCode + " | " + semester + " | Group " + group;
        }
    }
    public record TranscriptCoverage(int includedOfferings, int assignedOfferings,
                                     java.util.List<OfferingGap> missingAssignments) {
        public TranscriptCoverage { missingAssignments = java.util.List.copyOf(missingAssignments); }
        public boolean complete() { return includedOfferings > 0 && missingAssignments.isEmpty(); }
    }

    /** Metadata only. The service limits diagnostic candidates to the lecturer's existing roster. */
    public TranscriptCoverage transcriptCoverage(Connection c, int lecturer, int student) throws SQLException {
        var missing = new java.util.ArrayList<OfferingGap>();
        int included = 0, assigned = 0;
        try (var p = c.prepareStatement("""
                SELECT DISTINCT o.offering_id,cr.course_code,
                       CONCAT(ay.academic_year_name,' - ',COALESCE(s.semester_name,CONCAT('Semester ',s.semester_no))),
                       o.offering_group
                FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
                LEFT JOIN courses cr ON cr.course_id=o.course_id
                LEFT JOIN semesters s ON s.semester_id=o.semester_id
                LEFT JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
                WHERE e.student_id=? AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
                ORDER BY o.offering_id
                """)) {
            p.setInt(1,student); p.setQueryTimeout(15);
            try (var rows = p.executeQuery()) {
                while (rows.next()) {
                    included++;
                    if (new CourseResultDAO().lockLecturerAssignment(c,lecturer,rows.getInt(1))) assigned++;
                    else missing.add(new OfferingGap(rows.getInt(1),rows.getString(2),rows.getString(3),rows.getString(4)));
                }
            }
        }
        return new TranscriptCoverage(included,assigned,missing);
    }

    public int lecturerAssignmentCount(Connection c, SessionManager.SessionIdentity identity) throws SQLException {
        if (!"LECTURER".equals(actor(c,identity).getRole())) throw new SecurityException("Lecturer access required.");
        try (var p = c.prepareStatement("SELECT lecturer_id FROM lecturers WHERE lecturer_id=? FOR SHARE")) {
            p.setInt(1,identity.userId()); p.setQueryTimeout(15);
            try (var rows = p.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Your lecturer profile is missing. Please contact Admin.");
            }
        }
        try (var p = c.prepareStatement("""
                SELECT COUNT(DISTINCT a.offering_id) FROM lecturer_course_assignments a
                JOIN course_offerings o ON o.offering_id=a.offering_id
                WHERE a.lecturer_id=? AND o.status<>'CANCELLED'
                AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL')
                """)) {
            p.setInt(1,identity.userId()); p.setQueryTimeout(15);
            try (var rows = p.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
    public java.util.List<Integer> offeringEnrollments(Connection c,int offering)throws SQLException {
        var ids=new java.util.ArrayList<Integer>();
        try(var p=c.prepareStatement("SELECT enrollment_id FROM enrollments WHERE offering_id=? FOR UPDATE")) {
            p.setInt(1,offering);try(var r=p.executeQuery()){while(r.next())ids.add(r.getInt(1));}
        }
        return ids;
    }
    public User actor(Connection c, SessionManager.SessionIdentity identity) throws SQLException {
        SessionManager.requireSession(identity);
        User actor = new CourseResultDAO().lockActiveActor(c, identity.userId());
        User session = SessionManager.getCurrentUser();
        if (actor == null || session == null || !actor.getRole().equals(session.getRole()))
            throw new SecurityException("Your account or role has changed. Sign in again.");
        return actor;
    }

    public void offering(Connection c, SessionManager.SessionIdentity identity, int offering) throws SQLException {
        User actor = actor(c, identity);
        if (!"LECTURER".equals(actor.getRole()) ||
                !new CourseResultDAO().lockLecturerAssignment(c, identity.userId(), offering))
            throw new SecurityException("An active lecturer assigned to this offering is required.");
        try (var p = c.prepareStatement("SELECT status FROM course_offerings WHERE offering_id=? FOR UPDATE")) {
            p.setInt(1, offering);
            try (var r = p.executeQuery()) {
                if (!r.next() || "CANCELLED".equals(r.getString(1)))
                    throw new IllegalArgumentException("The offering is unavailable or cancelled.");
            }
        }
    }

    public int enrollment(Connection c, int enrollment) throws SQLException {
        try (var p = c.prepareStatement("SELECT offering_id,status FROM enrollments WHERE enrollment_id=? FOR UPDATE")) {
            p.setInt(1, enrollment);
            try (var r = p.executeQuery()) {
                if (!r.next() || "WITHDRAWN".equals(r.getString(2)))
                    throw new IllegalArgumentException("The enrollment is unavailable or withdrawn.");
                return r.getInt(1);
            }
        }
    }

    public void unpublished(Connection c, int enrollment) throws SQLException {
        var result = new CourseResultDAO().getByEnrollmentId(c, enrollment, true);
        if (result != null && result.isPublished())
            throw new PublishedResultProtectedException();
    }

    /** Source edits invalidate eligibility and drafts, while published rows must be rejected first. */
    public void invalidate(Connection c, int enrollment) throws SQLException {
        try (var p = c.prepareStatement("""
                UPDATE eligibility_records SET ca_score=NULL,ca_eligible=NULL,overall_eligible=NULL,
                reason='Assessment or marks changed; recalculate eligibility.',calculated_at=CURRENT_TIMESTAMP
                WHERE enrollment_id=?
                """)) {
            p.setInt(1, enrollment); p.executeUpdate();
        }
        invalidateDraft(c, enrollment);
    }

    public void invalidateDraft(Connection c, int enrollment) throws SQLException {
        try (var p = c.prepareStatement("""
                UPDATE course_results SET ca_mark=NULL,final_exam_mark=NULL,overall_mark=NULL,
                grade_rule_id=NULL,grade_letter=NULL,grade_point=NULL,result_status='PENDING',
                calculated_at=CURRENT_TIMESTAMP WHERE enrollment_id=? AND published=FALSE
                """)) {
            p.setInt(1, enrollment); p.executeUpdate();
        }
    }

    /** Full GPA is visible only to the student or a lecturer assigned to every included offering. */
    public void transcript(Connection c, SessionManager.SessionIdentity identity, int student, boolean write) throws SQLException {
        User actor = actor(c, identity);
        if (!write && "STUDENT".equals(actor.getRole()) && student == identity.userId()) {
            try(var p=c.prepareStatement("SELECT student_id FROM students WHERE student_id=? FOR SHARE")) {
                p.setInt(1,student);
                try(var rows=p.executeQuery()) {
                    if(!rows.next()) throw new SecurityException("Your student profile is unavailable. Contact the administrator.");
                }
            }
            return;
        }
        if (!"LECTURER".equals(actor.getRole())) throw new SecurityException("This academic record is not accessible.");
        var coverage = transcriptCoverage(c,identity.userId(),student);
        if (coverage.includedOfferings() == 0) throw new IllegalArgumentException("No active enrollments found.");
        if (!coverage.complete())
            throw new SecurityException("Full GPA includes offerings outside your assignment. Use assigned course reports; ask Admin to review your GPA access.");
    }
}
