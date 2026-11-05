package lk.ac.ruhuna.ftms.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lk.ac.ruhuna.ftms.dao.AssessmentDAO;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO;
import lk.ac.ruhuna.ftms.dao.EnrollmentDAO.StudentCourse;
import lk.ac.ruhuna.ftms.model.Assessment;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

/** Read-only API bound to one student login. No caller-supplied student identity or marks. */
public final class StudentAssessmentService {
    public record ReleasedAssessment(StudentCourse course, Assessment assessment) {}
    private final StudentAccessService access;
    private final EnrollmentDAO enrollments = new EnrollmentDAO();
    private final AssessmentDAO assessments = new AssessmentDAO();
    private static final Set<String> READABLE = Set.of("ENROLLED", "REPEAT", "COMPLETED");

    public StudentAssessmentService() { access = new StudentAccessService(); }
    public StudentAssessmentService(SessionIdentity identity) { access = new StudentAccessService(identity); }

    public List<ReleasedAssessment> getMyAssessments() throws SQLException {
        return access.read((c,student)->{
            var rows = new ArrayList<ReleasedAssessment>();
            var seen = new HashSet<Integer>();
            for (var course : enrollments.findByStudent(c,student)) {
                if (READABLE.contains(course.status()) && seen.add(course.offeringId())) {
                    for (var assessment : readOffering(c,student,course.offeringId()))
                        rows.add(new ReleasedAssessment(course,assessment));
                }
            }
            return List.copyOf(rows);
        });
    }

    /** Explicit offering access also checks enrollment; altering the ID never broadens access. */
    public List<Assessment> getAssessments(int offering) throws SQLException {
        return access.read((c,student)->readOffering(c,student,offering));
    }

    private List<Assessment> readOffering(Connection c,int student,int offering) throws SQLException {
        enrollments.requireAssessmentAccess(c,student,offering);
        return assessments.findReleasedForStudent(c,student,offering);
    }
}
