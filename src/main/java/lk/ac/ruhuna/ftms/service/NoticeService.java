package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.Notice.Audience;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class NoticeService {

    private final NoticeDAO noticeDAO;
    private final SessionManager.SessionIdentity identity;

    public NoticeService(NoticeDAO noticeDAO) {
        this.noticeDAO = Objects.requireNonNull(noticeDAO);
        this.identity = SessionManager.requireSession();
        requireViewerAccess();
    }

    // Authorization comes from the authenticated session, never a caller-supplied flag.
    public void requireAdminAccess() {
        requireViewerAccess();
        if (!"ADMIN".equals(SessionManager.getCurrentUser().getRole())) {
            throw new SecurityException(
                    "Access denied. Only Admin users can manage notices."
            );
        }
    }

    public void requireViewerAccess() {
        SessionManager.requireSession(identity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || user.getRole() == null
                || !Set.of("ADMIN", "LECTURER", "STUDENT", "TECHNICAL_OFFICER").contains(user.getRole())) {
            throw new SecurityException("Please sign in with an active FTMS account to view notices.");
        }
    }

    // Validate notice details
    private void validateNotice(Notice notice) {
        if (notice == null) {
            throw new IllegalArgumentException(
                    "Notice cannot be empty."
            );
        }

        String title = notice.getTitle();
        String content = notice.getContent();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter a notice title."
            );
        }

        if (title.trim().codePointCount(0, title.trim().length()) > 180) {
            throw new IllegalArgumentException(
                    "Notice title cannot exceed 180 characters."
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter notice content."
            );
        }

        if (content.trim().getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new IllegalArgumentException("Notice content exceeds the database text limit (65,535 UTF-8 bytes).");
        }
        if (notice.getNoticeId() < 0) {
            throw new IllegalArgumentException("Invalid notice ID.");
        }
        if (notice.getAudience() == null) {
            throw new IllegalArgumentException("Please select a notice audience.");
        }
        if (notice.getNoticeId() > 0 && notice.getPublishedAt() == null) {
            throw new IllegalArgumentException("Please retain or set the existing notice's publication start.");
        }
        if (notice.getPublishedAt() != null && notice.getExpiresAt() != null
                && !notice.getExpiresAt().isAfter(notice.getPublishedAt())) {
            throw new IllegalArgumentException("Expiry must be after the publication start.");
        }

        notice.setTitle(title.trim());
        notice.setContent(content.trim());
    }

    // Retrieve all notices
    public List<Notice> getAllNotices() throws SQLException {
        requireAdminAccess();
        return noticeDAO.getAllNotices();
    }

    // Search notices
    public List<Notice> searchNotices(String keyword)
            throws SQLException {
        requireAdminAccess();

        if (keyword == null || keyword.isBlank()) {
            return noticeDAO.getAllNotices();
        }

        return noticeDAO.searchNotices(keyword.trim());
    }

    // Create or update a notice
    public boolean saveNotice(Notice notice) throws SQLException {
        requireAdminAccess();
        validateNotice(notice);

        return noticeDAO.saveNotice(notice, identity.userId());
    }

    // Publish or unpublish a notice
    public boolean setPublished(long noticeId, boolean published)
            throws SQLException {
        requireAdminAccess();

        if (noticeId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid notice ID."
            );
        }

        return noticeDAO.setPublished(noticeId, published);
    }

    // Delete a notice
    public boolean deleteNotice(long noticeId) throws SQLException {
        requireAdminAccess();

        if (noticeId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid notice ID."
            );
        }

        return noticeDAO.deleteNotice(noticeId);
    }
    // Allow active logged-in users to view published notices
    public List<Notice> getPublishedNotices() throws SQLException {

        requireViewerAccess();
        Audience audience = switch (SessionManager.getCurrentUser().getRole()) {
            case "ADMIN" -> Audience.ADMINS;
            case "LECTURER" -> Audience.LECTURERS;
            case "STUDENT" -> Audience.STUDENTS;
            case "TECHNICAL_OFFICER" -> Audience.TECHNICAL_OFFICERS;
            default -> throw new SecurityException("Unsupported notice audience.");
        };
        return noticeDAO.getPublishedNotices(audience);
    }
}
