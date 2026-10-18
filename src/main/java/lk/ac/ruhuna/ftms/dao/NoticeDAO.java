package lk.ac.ruhuna.ftms.dao;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.Notice.Audience;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NoticeDAO {
    private static final String COLUMNS =
            "notice_id, title, content, audience, created_by, published_at, expires_at, is_active";

    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    public List<Notice> getAllNotices() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM notices ORDER BY published_at DESC, notice_id DESC";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return readNotices(rs);
        }
    }

    public List<Notice> searchNotices(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + """
                 FROM notices WHERE title LIKE ? OR content LIKE ?
                 ORDER BY published_at DESC, notice_id DESC
                """;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + keyword + "%");
            stmt.setString(2, "%" + keyword + "%");
            try (ResultSet rs = stmt.executeQuery()) { return readNotices(rs); }
        }
    }

    /** The service supplies the audience derived from the authenticated user's role. */
    public List<Notice> getPublishedNotices(Audience audience) throws SQLException {
        Objects.requireNonNull(audience, "Audience is required.");
        String sql = "SELECT " + COLUMNS + """
                 FROM notices
                 WHERE is_active = 1
                   AND published_at <= CURRENT_TIMESTAMP
                   AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                   AND (audience = 'ALL' OR audience = ?)
                 ORDER BY published_at DESC, notice_id DESC
                """;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, audience.name());
            try (ResultSet rs = stmt.executeQuery()) { return readNotices(rs); }
        }
    }

    public boolean saveNotice(Notice notice, int creatorId) throws SQLException {
        if (notice == null || notice.getTitle() == null || notice.getTitle().isBlank()
                || notice.getContent() == null || notice.getContent().isBlank() || notice.getAudience() == null) {
            throw new IllegalArgumentException("Notice title, content and audience are required.");
        }
        if (notice.getNoticeId() > 0) return updateNotice(notice);

        String sql = """
                INSERT INTO notices (title, content, audience, created_by, published_at, expires_at, is_active)
                VALUES (?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, notice.getTitle().trim());
            stmt.setString(2, notice.getContent().trim());
            stmt.setString(3, notice.getAudience().name());
            stmt.setInt(4, creatorId);
            setTimestamp(stmt, 5, notice.getPublishedAt());
            setTimestamp(stmt, 6, notice.getExpiresAt());
            stmt.setBoolean(7, notice.isActive());
            if (stmt.executeUpdate() == 0) return false;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) notice.setNoticeId(keys.getLong(1));
            }
            notice.setCreatedBy(creatorId);
            return true;
        }
    }

    private boolean updateNotice(Notice notice) throws SQLException {
        String sql = """
                UPDATE notices
                SET title = ?, content = ?, audience = ?, published_at = ?, expires_at = ?, is_active = ?
                WHERE notice_id = ?
                """;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, notice.getTitle().trim());
            stmt.setString(2, notice.getContent().trim());
            stmt.setString(3, notice.getAudience().name());
            setTimestamp(stmt, 4, notice.getPublishedAt());
            setTimestamp(stmt, 5, notice.getExpiresAt());
            stmt.setBoolean(6, notice.isActive());
            stmt.setLong(7, notice.getNoticeId());
            return stmt.executeUpdate() > 0;
        }
    }

    /** Enable/disable publication without moving its scheduled start or expiry. */
    public boolean setPublished(long noticeId, boolean published) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE notices SET is_active = ? WHERE notice_id = ?")) {
            stmt.setBoolean(1, published);
            stmt.setLong(2, noticeId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteNotice(long noticeId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM notices WHERE notice_id = ?")) {
            stmt.setLong(1, noticeId);
            return stmt.executeUpdate() > 0;
        }
    }

    private static void setTimestamp(PreparedStatement stmt, int index, java.time.LocalDateTime value) throws SQLException {
        if (value == null) stmt.setNull(index, Types.TIMESTAMP);
        else stmt.setTimestamp(index, Timestamp.valueOf(value));
    }

    private List<Notice> readNotices(ResultSet rs) throws SQLException {
        List<Notice> notices = new ArrayList<>();
        while (rs.next()) notices.add(mapNotice(rs));
        return notices;
    }

    private Notice mapNotice(ResultSet rs) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new Notice(rs.getLong("notice_id"), rs.getString("title"), rs.getString("content"),
                Audience.valueOf(rs.getString("audience")), rs.getInt("created_by"),
                publishedAt == null ? null : publishedAt.toLocalDateTime(),
                expiresAt == null ? null : expiresAt.toLocalDateTime(), rs.getBoolean("is_active"));
    }
}
