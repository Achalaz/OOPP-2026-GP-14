package lk.ac.ruhuna.ftms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/ftms_group14";

    private static final String USER = "ftms_app";

    private static final String PASSWORD = "Ftms@2026";

    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}