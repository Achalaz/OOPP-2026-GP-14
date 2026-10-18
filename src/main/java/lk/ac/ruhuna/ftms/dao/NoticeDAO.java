package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Notice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class NoticeDAO {

    // Get all notices
    public List<Notice> getAllNotices() throws SQLException {
        List<Notice> notices = new ArrayList<>();

        String sql = """
                SELECT notice_id, title, content, is_published,
                       created_at, updated_at
                FROM notices
                ORDER BY created_at DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                notices.add(mapNotice(rs));
            }
        }

        return notices;
    }

    // Search notices by title or content
    public List<Notice> searchNotices(String keyword)
            throws SQLException {

        List<Notice> notices = new ArrayList<>();

        String sql = """
                SELECT notice_id, title, content, is_published,
                       created_at, updated_at
                FROM notices
                WHERE title LIKE ? OR content LIKE ?
                ORDER BY created_at DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String searchValue = "%" + keyword + "%";

            stmt.setString(1, searchValue);
            stmt.setString(2, searchValue);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notices.add(mapNotice(rs));
                }
            }
        }

        return notices;
    }

    // Create a new notice or update an existing notice
    public boolean saveNotice(Notice notice) throws SQLException {

        if (notice.getTitle() == null
                || notice.getTitle().isBlank()
                || notice.getContent() == null
                || notice.getContent().isBlank()) {
            throw new IllegalArgumentException(
                    "Notice title and content are required."
            );
        }

        if (notice.getNoticeId() > 0) {
            return updateNotice(notice);
        }

        String sql = """
                INSERT INTO notices (title, content, is_published)
                VALUES (?, ?, ?)
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, notice.getTitle().trim());
            stmt.setString(2, notice.getContent().trim());
            stmt.setBoolean(3, notice.isPublished());

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        notice.setNoticeId(keys.getLong(1));
                    }
                }
                return true;
            }
        }

        return false;
    }

    // Update an existing notice
    private boolean updateNotice(Notice notice)
            throws SQLException {

        String sql = """
                UPDATE notices
                SET title = ?, content = ?, is_published = ?
                WHERE notice_id = ?
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, notice.getTitle().trim());
            stmt.setString(2, notice.getContent().trim());
            stmt.setBoolean(3, notice.isPublished());
            stmt.setLong(4, notice.getNoticeId());

            return stmt.executeUpdate() > 0;
        }
    }

    // Publish or unpublish a notice
    public boolean setPublished(long noticeId, boolean published)
            throws SQLException {

        String sql = """
                UPDATE notices
                SET is_published = ?
                WHERE notice_id = ?
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, published);
            stmt.setLong(2, noticeId);

            return stmt.executeUpdate() > 0;
        }
    }

    // Delete a notice
    public boolean deleteNotice(long noticeId) throws SQLException {

        String sql = """
                DELETE FROM notices
                WHERE notice_id = ?
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, noticeId);

            return stmt.executeUpdate() > 0;
        }
    }

    // Convert a database row into a Notice object
    private Notice mapNotice(ResultSet rs) throws SQLException {

        Notice notice = new Notice();

        notice.setNoticeId(rs.getLong("notice_id"));
        notice.setTitle(rs.getString("title"));
        notice.setContent(rs.getString("content"));
        notice.setPublished(rs.getBoolean("is_published"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            notice.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            notice.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return notice;
    }
    // Get published notices only for general users
    public List<Notice> getPublishedNotices() throws SQLException {

        List<Notice> notices = new ArrayList<>();

        String sql = """
            SELECT notice_id, title, content, is_published,
                   created_at, updated_at
            FROM notices
            WHERE is_published = TRUE
            ORDER BY created_at DESC
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                notices.add(mapNotice(rs));
            }
        }

        return notices;
    }
}