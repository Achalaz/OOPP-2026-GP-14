package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Explicit dependency-free runner, following the project's existing test convention. */
public final class NoticeServiceTest {
    private static int checks;
    public static final List<String> ROLES = List.of("ADMIN", "LECTURER", "STUDENT", "TECHNICAL_OFFICER");

    public static void main(String[] args) throws Exception {
        MemoryDAO dao = new MemoryDAO();
        try {
            SessionManager.logout();
            expect(SecurityException.class, () -> new NoticeService(dao));
            for (String role : List.of("UNKNOWN", "", "admin")) {
                SessionManager.startSession(user(role));
                expect(SecurityException.class, () -> new NoticeService(dao));
            }
            User inactive = user("ADMIN");
            inactive.setActive(false);
            expect(IllegalArgumentException.class, () -> SessionManager.startSession(inactive));
            User noRole = user(null);
            SessionManager.startSession(noRole);
            expect(SecurityException.class, () -> new NoticeService(dao));

            SessionManager.startSession(user("ADMIN"));
            NoticeService admin = new NoticeService(dao);
            Notice draft = notice("  Meeting  ", "  Full meeting content  ");
            check(admin.saveNotice(draft), "create draft");
            long id = draft.getNoticeId();
            check(id > 0 && dao.creatorId == 7, "generated ID and authenticated creator");
            check("Meeting".equals(draft.getTitle()) && "Full meeting content".equals(draft.getContent()), "trim input");
            check(admin.getAllNotices().size() == 1 && admin.getPublishedNotices().isEmpty(), "admin sees draft only in management");
            check(admin.searchNotices(" Meeting ").size() == 1, "search title");
            check(admin.searchNotices("Full meeting").size() == 1, "search content");
            check(admin.searchNotices(null).size() == 1 && admin.searchNotices(" ").size() == 1, "blank search loads all");
            check(admin.searchNotices("missing").isEmpty(), "empty search result");

            for (String role : ROLES.subList(1, ROLES.size())) {
                SessionManager.startSession(user(role));
                NoticeService reader = new NoticeService(dao);
                check(reader.getPublishedNotices().isEmpty(), role + " cannot see draft");
                int calls = dao.calls;
                expect(SecurityException.class, reader::getAllNotices);
                expect(SecurityException.class, () -> reader.searchNotices("Meeting"));
                expect(SecurityException.class, () -> reader.saveNotice(notice("New", "Text")));
                expect(SecurityException.class, () -> reader.saveNotice(draft));
                expect(SecurityException.class, () -> reader.setPublished(id, true));
                expect(SecurityException.class, () -> reader.setPublished(id, false));
                expect(SecurityException.class, () -> reader.deleteNotice(id));
                check(calls == dao.calls, role + " forbidden calls never reach DAO");
            }
            SessionManager.startSession(user("ADMIN"));
            NoticeService staleAdmin = admin;
            expect(SecurityException.class, () -> staleAdmin.saveNotice(draft)); // previous login is stale
            admin = new NoticeService(dao);
            check(admin.setPublished(id, true), "publish");
            readersSee(dao, 1, "Full meeting content");
            SessionManager.startSession(user("ADMIN"));
            admin = new NoticeService(dao);
            Notice edited = admin.getAllNotices().getFirst();
            edited.setTitle("Updated title");
            edited.setContent("Updated full content");
            check(admin.saveNotice(edited), "edit published notice");
            check(edited.getNoticeId() == id && admin.getAllNotices().size() == 1, "edit retains ID without insert");
            readersSee(dao, 1, "Updated full content");
            SessionManager.startSession(user("ADMIN"));
            admin = new NoticeService(dao);
            check(admin.setPublished(id, false), "unpublish");
            readersSee(dao, 0, "");
            SessionManager.startSession(user("ADMIN"));
            NoticeService service = new NoticeService(dao);
            for (String title : new String[]{null, "", " ", "a".repeat(181)}) {
                expect(IllegalArgumentException.class, () -> service.saveNotice(notice(title, "Content")));
            }
            for (String content : new String[]{null, "", " ", "a".repeat(65_536), "\u20ac".repeat(21_846)}) {
                expect(IllegalArgumentException.class, () -> service.saveNotice(notice("Title", content)));
            }
            expect(IllegalArgumentException.class, () -> service.saveNotice(null));
            Notice invalidId = notice("Title", "Content"); invalidId.setNoticeId(-1);
            expect(IllegalArgumentException.class, () -> service.saveNotice(invalidId));
            for (long bad : new long[]{0, -1}) {
                expect(IllegalArgumentException.class, () -> service.deleteNotice(bad));
                expect(IllegalArgumentException.class, () -> service.setPublished(bad, true));
            }
            check(service.saveNotice(notice("a".repeat(180), "a".repeat(65_535))), "schema boundary values accepted");
            Notice published = notice("Published at creation", "Content"); published.setActive(true);
            check(service.saveNotice(published) && service.getPublishedNotices().size() == 1, "save as published");
            check(service.deleteNotice(id) && !service.deleteNotice(id), "delete and missing ID");
            SessionManager.logout();
            expect(SecurityException.class, service::getPublishedNotices);
            expect(SecurityException.class, service::getAllNotices);
            expect(SecurityException.class, () -> service.saveNotice(draft));
            verifyVisibility();
            System.out.println("Notice service checks passed: " + checks);
        } finally { SessionManager.logout(); }
    }

    private static void readersSee(MemoryDAO dao, int count, String content) throws Exception {
        for (String role : ROLES) {
            SessionManager.startSession(user(role));
            List<Notice> notices = new NoticeService(dao).getPublishedNotices();
            check(notices.size() == count, role + " published count");
            if (count > 0) check(content.equals(notices.getFirst().getContent()), role + " content updated");
        }
    }

    private static void verifyVisibility() throws Exception {
        MemoryDAO dao = new MemoryDAO();
        dao.now = LocalDateTime.of(2026, 10, 18, 12, 0);
        SessionManager.startSession(user("ADMIN"));
        NoticeService admin = new NoticeService(dao);
        for (Notice.Audience audience : Notice.Audience.values()) {
            Notice row = notice(audience.name(), "Audience content");
            row.setAudience(audience); row.setActive(true); row.setPublishedAt(dao.now);
            row.setCreatedBy(999);
            check(admin.saveNotice(row) && row.getCreatedBy() == 7, "creator cannot be supplied by form");
        }
        Notice future = notice("Future", "Scheduled"); future.setActive(true); future.setPublishedAt(dao.now.plusSeconds(1));
        admin.saveNotice(future);
        Notice expired = notice("Expired", "At exact expiry"); expired.setActive(true);
        expired.setPublishedAt(dao.now.minusDays(1)); expired.setExpiresAt(dao.now); admin.saveNotice(expired);
        Notice inactive = notice("Inactive", "Not enabled"); inactive.setPublishedAt(dao.now.minusDays(1)); admin.saveNotice(inactive);
        Notice validExpiry = notice("Valid expiry", "Visible until next second"); validExpiry.setActive(true);
        validExpiry.setPublishedAt(dao.now); validExpiry.setExpiresAt(dao.now.plusSeconds(1)); admin.saveNotice(validExpiry);
        check(future.publicationStatus(dao.now).equals("Scheduled"), "scheduled status");
        check(expired.publicationStatus(dao.now).equals("Expired"), "expiry equality is expired");
        check(inactive.publicationStatus(dao.now).equals("Draft / Inactive"), "inactive status");
        check(validExpiry.publicationStatus(dao.now).equals("Published"), "publication equality is visible");
        for (String role : ROLES) {
            SessionManager.startSession(user(role));
            String audience = switch (role) {
                case "ADMIN" -> "ADMINS";
                case "LECTURER" -> "LECTURERS";
                case "STUDENT" -> "STUDENTS";
                default -> "TECHNICAL_OFFICERS";
            };
            var titles = new NoticeService(dao).getPublishedNotices().stream().map(Notice::getTitle).toList();
            check(titles.size() == 3 && titles.containsAll(List.of("ALL", audience, "Valid expiry")), role + " exact audience/date filtering");
        }
        SessionManager.startSession(user("ADMIN")); admin = new NoticeService(dao);
        admin.setPublished(future.getNoticeId(), false); admin.setPublished(future.getNoticeId(), true);
        check(admin.getPublishedNotices().stream().noneMatch(n -> n.getTitle().equals("Future")), "publish preserves future schedule");
        NoticeService validated = admin;
        Notice missingAudience = notice("Title", "Body"); missingAudience.setAudience(null);
        expect(IllegalArgumentException.class, () -> validated.saveNotice(missingAudience));
        Notice invalidDates = notice("Title", "Body"); invalidDates.setPublishedAt(dao.now); invalidDates.setExpiresAt(dao.now);
        expect(IllegalArgumentException.class, () -> validated.saveNotice(invalidDates));
        invalidDates.setExpiresAt(dao.now.minusSeconds(1));
        expect(IllegalArgumentException.class, () -> validated.saveNotice(invalidDates));
        Notice missingStart = notice("Title", "Body"); missingStart.setNoticeId(1);
        expect(IllegalArgumentException.class, () -> validated.saveNotice(missingStart));
    }

    public static User user(String role) { return new User(7, "notice-test", "Notice Test", "test@example.com", role, true); }
    public static Notice notice(String title, String content) {
        return new Notice(0, title, content, Notice.Audience.ALL, 0, null, null, false);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void expect(Class<? extends Exception> type, Action action) throws Exception {
        try { action.run(); } catch (Exception error) {
            if (type.isInstance(error)) { checks++; return; }
            throw error;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }

    /** Detached records expose stale-selection bugs in the controllers. No database access. */
    public static class MemoryDAO extends NoticeDAO {
        public final List<Notice> rows = new ArrayList<>();
        public int calls;
        public int creatorId;
        public boolean failReads;
        private long sequence;
        public LocalDateTime now = LocalDateTime.now().withNano(0);
        private static Notice copy(Notice row) {
            return new Notice(row.getNoticeId(), row.getTitle(), row.getContent(), row.getAudience(), row.getCreatedBy(), row.getPublishedAt(), row.getExpiresAt(), row.isActive());
        }
        @Override public List<Notice> getAllNotices() throws SQLException {
            calls++;
            if (failReads) throw new SQLException("Simulated outage");
            return rows.stream().map(MemoryDAO::copy).toList();
        }
        @Override public List<Notice> getPublishedNotices(Notice.Audience audience) throws SQLException {
            return getAllNotices().stream().filter(n -> n.isActive()
                    && n.getPublishedAt() != null && !n.getPublishedAt().isAfter(now)
                    && (n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                    && (n.getAudience() == Notice.Audience.ALL || n.getAudience() == audience)).toList();
        }
        @Override public List<Notice> searchNotices(String keyword) throws SQLException {
            return getAllNotices().stream().filter(n -> n.getTitle().contains(keyword) || n.getContent().contains(keyword)).toList();
        }
        @Override public boolean saveNotice(Notice notice, int creator) {
            calls++; creatorId = creator;
            if (notice.getNoticeId() == 0) {
                notice.setNoticeId(++sequence); notice.setCreatedBy(creator);
                if (notice.getPublishedAt() == null) notice.setPublishedAt(now);
            } else if (!rows.removeIf(n -> n.getNoticeId() == notice.getNoticeId())) return false;
            rows.add(copy(notice)); return true;
        }
        @Override public boolean setPublished(long id, boolean published) {
            calls++;
            for (Notice row : rows) if (row.getNoticeId() == id) { row.setActive(published); return true; }
            return false;
        }
        @Override public boolean deleteNotice(long id) { calls++; return rows.removeIf(n -> n.getNoticeId() == id); }
    }
}
