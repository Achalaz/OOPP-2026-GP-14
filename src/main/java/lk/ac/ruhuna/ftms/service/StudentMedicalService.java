
package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.MedicalDAO;
import lk.ac.ruhuna.ftms.model.Medical;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Read-only access to medical records
 * owned by the logged-in student.
 */
public final class StudentMedicalService {

    private final MedicalDAO medicalDAO;
    private final SessionIdentity loginIdentity;

    public StudentMedicalService() {
        this(new MedicalDAO());
    }

    public StudentMedicalService(MedicalDAO medicalDAO) {

        this.medicalDAO = Objects.requireNonNull(
                medicalDAO,
                "MedicalDAO is required."
        );

        this.loginIdentity =
                SessionManager.requireSession();

        requireStudent();
    }

    /**
     * There is deliberately no studentId argument.
     * The ID comes from the authenticated session.
     */
    public List<Medical> getMyMedicals()
            throws SQLException {

        int studentId = requireStudent();

        List<Medical> result =
                medicalDAO.findByStudentId(studentId);

        requireStudent();

        return List.copyOf(result);
    }

    private int requireStudent() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole())
                || user.getUserId()
                != loginIdentity.userId()) {

            throw new SecurityException(
                    "An active student session is required "
                            + "to view medical records."
            );
        }

        return loginIdentity.userId();
    }
}
