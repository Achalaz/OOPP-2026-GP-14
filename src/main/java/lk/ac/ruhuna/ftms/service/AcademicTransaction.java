package lk.ac.ruhuna.ftms.service;

import java.sql.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.util.SessionManager;

/** One login and one serializable transaction for a complete academic operation. */
final class AcademicTransaction {
    @FunctionalInterface interface Work<T> {
        T run(Connection c, SessionManager.SessionIdentity identity) throws SQLException;
    }
    static <T> T run(Work<T> work) throws SQLException {
        var identity = SessionManager.requireSession();
        try (var c = DBConnection.getConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            c.setAutoCommit(false);
            try {
                T result = work.run(c, identity);
                SessionManager.requireSession(identity);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { c.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }
}
