package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class NoticeService {

    private final NoticeDAO noticeDAO;
    private final BooleanSupplier isAdmin;

    public NoticeService(NoticeDAO noticeDAO,
                         BooleanSupplier isAdmin) {
        this.noticeDAO = Objects.requireNonNull(noticeDAO);
        this.isAdmin = Objects.requireNonNull(isAdmin);
    }

    // Check whether the logged-in user is an Admin
    private void requireAdmin() {
        if (!isAdmin.getAsBoolean()) {
            throw new SecurityException(
                    "Access denied. Only Admin users can manage notices."
            );
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

        if (title.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Notice title cannot exceed 200 characters."
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter notice content."
            );
        }

        notice.setTitle(title.trim());
        notice.setContent(content.trim());
    }

    // Retrieve all notices
    public List<Notice> getAllNotices() throws SQLException {
        requireAdmin();
        return noticeDAO.getAllNotices();
    }

    // Search notices
    public List<Notice> searchNotices(String keyword)
            throws SQLException {
        requireAdmin();

        if (keyword == null || keyword.isBlank()) {
            return noticeDAO.getAllNotices();
        }

        return noticeDAO.searchNotices(keyword.trim());
    }

    // Create or update a notice
    public boolean saveNotice(Notice notice) throws SQLException {
        requireAdmin();
        validateNotice(notice);

        return noticeDAO.saveNotice(notice);
    }

    // Publish or unpublish a notice
    public boolean setPublished(long noticeId, boolean published)
            throws SQLException {
        requireAdmin();

        if (noticeId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid notice ID."
            );
        }

        return noticeDAO.setPublished(noticeId, published);
    }

    // Delete a notice
    public boolean deleteNotice(long noticeId) throws SQLException {
        requireAdmin();

        if (noticeId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid notice ID."
            );
        }

        return noticeDAO.deleteNotice(noticeId);
    }
}