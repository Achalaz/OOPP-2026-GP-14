package lk.ac.ruhuna.ftms.dao;

import java.sql.*;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

/** Shared transaction guards for academic services. No caller-supplied actor is trusted. */
public class AcademicAccessDAO {
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
            throw new IllegalStateException("Published academic results are protected. A formal correction policy is required.");
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
        int count = 0;
        try (var p = c.prepareStatement("""
                SELECT DISTINCT e.offering_id FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
                WHERE e.student_id=? AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
                """)) {
            p.setInt(1, student);
            try (var r = p.executeQuery()) {
                while (r.next()) {
                    count++;
                    if (!new CourseResultDAO().lockLecturerAssignment(c, identity.userId(), r.getInt(1)))
                        throw new SecurityException("Full GPA includes offerings outside your assignment. Use offering reports.");
                }
            }
        }
        if (count == 0) throw new IllegalArgumentException("No active enrollments found.");
    }
}
