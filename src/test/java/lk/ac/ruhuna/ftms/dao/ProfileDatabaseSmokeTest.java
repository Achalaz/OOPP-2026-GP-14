package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.ProfileService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit integration runner. All writes use one transaction and are always rolled back. */
public final class ProfileDatabaseSmokeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--rollback-write".equals(args[0])) {
            throw new IllegalArgumentException("Run explicitly with --rollback-write; no changes will be retained.");
        }
        Map<Integer, Map<String, String>> original;
        int roles = 0;
        try (Connection connection = DBConnection.getConnection()) {
            original = snapshot(connection);
            connection.setAutoCommit(false);
            try {
                // DAO try-with-resources closes this handle; the test owns the real transaction.
                Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                            if (method.getName().equals("close")) { return null; }
                            try { return method.invoke(connection, arguments); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                        });
                ProfileDAO dao = new ProfileDAO() {
                    @Override protected Connection openConnection() { return borrowed; }
                };
                List<User> accounts = accounts(connection);
                for (String role : new String[]{"ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"}) {
                    User account = accounts.stream().filter(user -> role.equals(user.getRole())).findFirst()
                            .orElseThrow(() -> new AssertionError("An active " + role + " account is needed"));
                    connection.rollback();
                    SessionManager.startSession(account);
                    ProfileService service = new ProfileService(dao);
                    User loaded = service.getMyProfile();
                    check(loaded.getUserId() == account.getUserId(), "owner read for " + role);
                    String email = "profile-smoke-" + UUID.randomUUID() + "@example.com";
                    service.updateMyProfile("Profile Smoke Check", email);
                    Map<Integer, Map<String, String>> changed = snapshot(connection);
                    Map<String, String> row = changed.get(account.getUserId());
                    check("Profile".equals(row.get("first_name")), "first name stored");
                    check("Smoke Check".equals(row.get("last_name")), "remaining names stored");
                    check(email.equals(row.get("email")), "email stored");
                    for (var entry : original.entrySet()) {
                        if (entry.getKey() == account.getUserId()) {
                            for (var column : entry.getValue().entrySet()) {
                                if (!List.of("first_name", "last_name", "email", "updated_at").contains(column.getKey())) {
                                    check(java.util.Objects.equals(column.getValue(), row.get(column.getKey())),
                                            "non-profile column preserved: " + column.getKey());
                                }
                            }
                        } else {
                            check(entry.getValue().equals(changed.get(entry.getKey())), "other account unchanged");
                        }
                    }
                    service.updateMyProfile("Profile Smoke Check", email);
                    check(email.equals(service.getMyProfile().getEmail()), "unchanged save succeeds");
                    service.updateMyProfile("Amara", email);
                    check(dao.getProfile(account.getUserId()).getFullName().equals("Amara"), "single-name round trip");

                    User other = accounts.stream().filter(user -> user.getUserId() != account.getUserId()
                            && user.getEmail() != null && !user.getEmail().isBlank()).findFirst().orElseThrow();
                    try {
                        service.updateMyProfile("Profile Smoke Check", other.getEmail());
                        throw new AssertionError("Duplicate email accepted");
                    } catch (SQLException expected) {
                        check(expected.getErrorCode() == 1062, "MySQL unique-email constraint enforced");
                    }
                    expectDenied(() -> dao.updateProfile(other.getUserId(), "Other", email));
                    expectDenied(() -> dao.getProfile(other.getUserId()));
                    try (var statement = connection.prepareStatement("UPDATE users SET is_active = FALSE WHERE user_id = ?")) {
                        statement.setInt(1, account.getUserId());
                        statement.executeUpdate();
                    }
                    check(!dao.updateProfile(account.getUserId(), "Inactive", email), "DAO refuses inactive account");
                    expectDenied(service::getMyProfile);
                    expectDenied(() -> service.updateMyProfile("Inactive", email));
                    roles++;
                }
            } finally {
                connection.rollback();
                SessionManager.logout();
            }
        }
        try (Connection verification = DBConnection.getConnection()) {
            check(original.equals(snapshot(verification)), "all user rows restored after rollback");
        }
        System.out.println("Profile MySQL checks passed: " + checks + " across " + roles
                + " roles; all writes rolled back and all users verified unchanged.");
    }

    static Map<Integer, Map<String, String>> snapshot(Connection connection) throws SQLException {
        Map<Integer, Map<String, String>> rows = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("SELECT * FROM users ORDER BY user_id");
             var result = statement.executeQuery()) {
            while (result.next()) {
                Map<String, String> columns = new LinkedHashMap<>();
                for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
                    columns.put(result.getMetaData().getColumnName(index), result.getString(index));
                }
                rows.put(result.getInt("user_id"), columns);
            }
        }
        return rows;
    }

    static List<User> accounts(Connection connection) throws SQLException {
        List<User> users = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT user_id, username, first_name, last_name, email, role FROM users WHERE is_active = TRUE");
             var result = statement.executeQuery()) {
            while (result.next()) {
                users.add(new User(result.getInt("user_id"), result.getString("username"),
                        (result.getString("first_name") + " " + result.getString("last_name")).trim(),
                        result.getString("email"), result.getString("role"), true));
            }
        }
        return users;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static void expectDenied(Work action) throws Exception {
        try { action.run(); }
        catch (SecurityException expected) { checks++; return; }
        throw new AssertionError("Owner/session restriction was not enforced");
    }

    private interface Work { void run() throws Exception; }
}
