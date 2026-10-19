package lk.ac.ruhuna.ftms.service;
import java.sql.SQLException;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.util.SessionManager;
/** Bound to one login; revalidates account and assignment for every operation. */
public class LecturerAccessService {
    /** Preserve other modules' behavior while preventing lecturer access to legacy unscoped operations. */
    public static void rejectUnscopedLecturerOperation() {
        var user=SessionManager.getCurrentUser();
        if(user!=null && "LECTURER".equals(user.getRole()))
            throw new SecurityException("Use the authorized read-only lecturer workspace for this operation.");
    }
    private final SessionManager.SessionIdentity identity = SessionManager.requireSession();
    public int requireLecturer() throws SQLException {
        SessionManager.requireSession(identity);
        var session = SessionManager.getCurrentUser();
        if (session == null || !session.isActive() || !"LECTURER".equals(session.getRole()))
            throw new SecurityException("Active lecturer access is required.");
        var user = new UserDAO().findUserById(identity.userId());
        SessionManager.requireSession(identity);
        if (user == null || !user.isActive() || !"LECTURER".equals(user.getRole()))
            throw new SecurityException("Your lecturer account is no longer active.");
        return identity.userId();
    }
    public void requireOffering(int offeringId) throws SQLException {
        int lecturer = requireLecturer();
        if (offeringId <= 0 || !new LecturerCourseAssignmentDAO().isAssigned(lecturer, offeringId))
            throw new SecurityException("You are not assigned to this course offering.");
        SessionManager.requireSession(identity);
    }
}
