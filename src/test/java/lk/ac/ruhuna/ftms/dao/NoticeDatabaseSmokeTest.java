package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.Notice.Audience;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.NoticeService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Real SELECT verification plus isolated INSERT/UPDATE/DELETE on connection-local temporary tables.
 * No migration, production writes, DROP, or TRUNCATE. Closing the connection removes its temporary tables. */
public final class NoticeDatabaseSmokeTest {
    private static int checks;
    private static final List<String> ROLES = List.of("ADMIN", "LECTURER", "STUDENT", "TECHNICAL_OFFICER");

    public static void main(String[] args) throws Exception {
        if (!List.of(args).equals(List.of("--temporary-table"))) {
            throw new IllegalArgumentException("Run explicitly with --temporary-table (real SELECTs; temporary writes only).");
        }
        // Session fixtures need no production accounts; all notice writes use the temporary table.
        List<User> users = java.util.stream.IntStream.range(0, ROLES.size())
                .mapToObj(i -> new User(i + 1, "notice-fixture-" + i, "Notice Fixture", null, ROLES.get(i), true))
                .toList();
        byte[] before = productionSnapshot();
        try {
            verifyRealSelects(users);
            verifyTemporaryWrites(users);
        } finally {
            SessionManager.logout();
            check(Arrays.equals(before, productionSnapshot()), "real notices schema and every column of every existing row unchanged");
        }
        System.out.println("Notice MySQL checks passed: " + checks + "; real table remains unchanged.");
    }

    private static void verifyRealSelects(List<User> users) throws Exception {
        try (Connection connection = DBConnection.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SHOW CREATE TABLE notices")) {
                check(result.next(), "notices exists");
                String schema = result.getString(2);
                check(schema.contains("`published_at`") && schema.contains("`is_active`") && schema.contains("`audience`"), "verified actual columns");
                check(!schema.contains("`is_published`") && !schema.contains("`created_at`") && !schema.contains("`updated_at`"), "no synthetic columns");
            }
        }
        // Real reads are smoke checks only. Row counts, titles and time boundaries belong to
        // the controlled temporary fixtures below, never to mutable production content.
        for (String role : ROLES) {
            SessionManager.startSession(role(users, role));
            List<Notice> notices = new NoticeService(new NoticeDAO()).getPublishedNotices();
            check(notices.stream().allMatch(n -> n.isActive()
                    && (n.getAudience() == Audience.ALL || n.getAudience() == audience(role))),
                    role + " real SELECT contains only active notices for its audience");
        }
        SessionManager.startSession(role(users, "ADMIN"));
        List<Notice> all = new NoticeService(new NoticeDAO()).getAllNotices();
        check(all.stream().map(Notice::getNoticeId).distinct().count() == all.size(), "admin real SELECT maps unique notice IDs");
    }

    private static void verifyTemporaryWrites(List<User> users) throws Exception {
        User admin = role(users, "ADMIN");
        try (Connection connection = DBConnection.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE ftms_notice_test_template LIKE notices");
                statement.execute("CREATE TEMPORARY TABLE notices LIKE ftms_notice_test_template");
                // Freeze CURRENT_TIMESTAMP only for this test connection; exact boundary assertions need no sleep.
                statement.execute("SET timestamp = UNIX_TIMESTAMP('2026-10-18 12:00:00')");
            }
            LocalDateTime now;
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT CURRENT_TIMESTAMP")) {
                result.next(); now = result.getTimestamp(1).toLocalDateTime();
            }
            Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("close")) return null;
                        try { return method.invoke(connection, arguments); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    });
            NoticeDAO dao = new NoticeDAO() {
                @Override protected Connection getConnection() { return borrowed; }
            };
            SessionManager.startSession(admin);
            NoticeService service = new NoticeService(dao);
            check(service.getAllNotices().isEmpty(), "temporary fixture starts empty regardless of production rows");
            Notice notice = row("MySQL lifecycle", Audience.ALL, null, null, false);
            notice.setCreatedBy(999);
            notice.setContent("Full content: ' quotes and \u0dc3\u0dd2\u0d82\u0dc4\u0dbd");
            check(service.saveNotice(notice), "draft INSERT with actual schema");
            long id = notice.getNoticeId();
            Notice stored = dao.getAllNotices().getFirst();
            check(id > 0 && stored.getNoticeId() == id && !stored.isActive(), "generated ID and inactive draft persisted");
            check(stored.getContent().equals(notice.getContent()), "Unicode and quotes round-trip");
            check(stored.getPublishedAt().equals(now) && stored.getExpiresAt() == null, "database default timestamp and nullable expiry");
            check(stored.getCreatedBy() == admin.getUserId(), "authenticated Admin overrides supplied creator");

            verifyReaders(users, dao, Set.of());
            SessionManager.startSession(admin); service = new NoticeService(dao);
            check(service.setPublished(id, true), "publish changes is_active");
            verifyReaders(users, dao, Set.of("MySQL lifecycle"));

            SessionManager.startSession(admin); service = new NoticeService(dao);
            stored = dao.getAllNotices().getFirst();
            stored.setTitle("Edited MySQL notice"); stored.setContent("Updated full content"); stored.setCreatedBy(999);
            check(service.saveNotice(stored), "UPDATE succeeds");
            check(dao.getAllNotices().size() == 1 && dao.getAllNotices().getFirst().getNoticeId() == id, "edit retains ID without duplicate");
            check(dao.getAllNotices().getFirst().getCreatedBy() == admin.getUserId(), "edit preserves original creator");
            check(dao.getAllNotices().getFirst().getPublishedAt().equals(now), "edit preserves publication timestamp");
            check(service.searchNotices("Edited").size() == 1 && service.searchNotices("Updated full").size() == 1, "title/content search");
            check(service.searchNotices("' OR 1=1 --").isEmpty(), "PreparedStatement prevents injection");
            verifyReaders(users, dao, Set.of("Edited MySQL notice"));

            SessionManager.startSession(admin); service = new NoticeService(dao);
            check(service.setPublished(id, false), "unpublish persists inactive");
            verifyReaders(users, dao, Set.of());
            SessionManager.startSession(admin); service = new NoticeService(dao);
            check(service.deleteNotice(id) && service.getAllNotices().isEmpty(), "DELETE and refresh");
            check(!service.deleteNotice(id) && !service.setPublished(id, true), "missing IDs cannot update/delete");

            for (Audience audience : Audience.values()) {
                Notice targeted = row(audience.name(), audience, now, null, true);
                check(service.saveNotice(targeted), "INSERT audience " + audience.name());
            }
            Notice future = row("Future", Audience.ALL, now.plusSeconds(1), null, true);
            service.saveNotice(future);
            service.saveNotice(row("Expired", Audience.ALL, now.minusDays(1), now, true));
            service.saveNotice(row("Inactive", Audience.ALL, now.minusDays(1), null, false));
            service.saveNotice(row("Valid expiry", Audience.ALL, now, now.plusSeconds(1), true));
            check(service.getAllNotices().size() == Audience.values().length + 4,
                    "admin management includes every controlled fixture, including scheduled/inactive/expired notices");
            for (String role : ROLES) {
                SessionManager.startSession(role(users, role));
                Set<String> titles = new NoticeService(dao).getPublishedNotices().stream().map(Notice::getTitle).collect(Collectors.toSet());
                check(titles.equals(Set.of("ALL", audience(role).name(), "Valid expiry")), role + " SQL audience and exact date boundaries");
            }

            SessionManager.startSession(admin); service = new NoticeService(dao);
            service.setPublished(future.getNoticeId(), false); service.setPublished(future.getNoticeId(), true);
            check(service.getPublishedNotices().stream().noneMatch(n -> n.getTitle().equals("Future")), "toggle never publishes future notice early");
            Notice targeted = service.searchNotices("STUDENTS").getFirst();
            targeted.setAudience(Audience.LECTURERS); service.saveNotice(targeted);
            SessionManager.startSession(role(users, "STUDENT"));
            check(new NoticeService(dao).getPublishedNotices().stream().noneMatch(n -> n.getNoticeId() == targeted.getNoticeId()), "audience edit removes student access");
            SessionManager.startSession(role(users, "LECTURER"));
            check(new NoticeService(dao).getPublishedNotices().stream().anyMatch(n -> n.getNoticeId() == targeted.getNoticeId()), "audience edit grants lecturer visibility");
            // Advance only this connection's clock by one second to test both boundaries.
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET timestamp = UNIX_TIMESTAMP('2026-10-18 12:00:01')");
            }
            Set<String> nextTitles = new NoticeService(dao).getPublishedNotices().stream().map(Notice::getTitle).collect(Collectors.toSet());
            check(nextTitles.contains("Future") && !nextTitles.contains("Valid expiry"), "scheduled start inclusive and expiry exclusive");
        }
    }

    private static Notice row(String title, Audience audience, LocalDateTime start, LocalDateTime expiry, boolean active) {
        return new Notice(0, title, "Test content", audience, 0, start, expiry, active);
    }

    private static void verifyReaders(List<User> users, NoticeDAO dao, Set<String> expected) throws Exception {
        for (String role : ROLES) {
            SessionManager.startSession(role(users, role));
            NoticeService reader = new NoticeService(dao);
            List<Notice> notices = reader.getPublishedNotices();
            check(notices.stream().map(Notice::getTitle).collect(Collectors.toSet()).equals(expected), role + " publication filter");
            if (expected.contains("Edited MySQL notice")) check(notices.getFirst().getContent().equals("Updated full content"), "edited content visible");
            if (!role.equals("ADMIN")) {
                expectDenied(reader::getAllNotices);
                expectDenied(() -> reader.searchNotices(""));
                Notice draft = row("Denied", Audience.ALL, null, null, false);
                expectDenied(() -> reader.saveNotice(draft));
                draft.setNoticeId(1);
                expectDenied(() -> reader.saveNotice(draft));
                expectDenied(() -> reader.setPublished(1, true));
                expectDenied(() -> reader.setPublished(1, false));
                expectDenied(() -> reader.deleteNotice(1));
            }
        }
    }

    private static Audience audience(String role) {
        return switch (role) {
            case "ADMIN" -> Audience.ADMINS;
            case "STUDENT" -> Audience.STUDENTS;
            case "LECTURER" -> Audience.LECTURERS;
            default -> Audience.TECHNICAL_OFFICERS;
        };
    }
    private static User role(List<User> users, String role) {
        return users.stream().filter(u -> u.isActive() && role.equals(u.getRole())).findFirst()
                .orElseThrow(() -> new AssertionError("Active fixture account required for " + role));
    }
    private static byte[] productionSnapshot() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = DBConnection.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : List.of("SHOW CREATE TABLE notices", "SELECT * FROM notices ORDER BY notice_id")) {
                try (ResultSet result = statement.executeQuery(sql)) {
                    while (result.next()) for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                        String value = result.getString(i);
                        digest.update((value == null ? "<NULL>" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                }
            }
        }
        return digest.digest();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void expectDenied(Action action) throws Exception {
        try { action.run(); } catch (SecurityException expected) { checks++; return; }
        throw new AssertionError("Non-admin operation allowed");
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
