package lk.ac.ruhuna.ftms.model;

import java.time.LocalDateTime;

/** A notice mapped to the existing notices table; active does not imply visible yet. */
public class Notice {
    public enum Audience {
        ALL("All users"), STUDENTS("Students"), LECTURERS("Lecturers"),
        TECHNICAL_OFFICERS("Technical Officers"), ADMINS("Administrators");

        private final String label;
        Audience(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private long noticeId;
    private String title;
    private String content;
    private Audience audience = Audience.ALL;
    private int createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private boolean active;

    public Notice() {}

    public Notice(long noticeId, String title, String content, Audience audience,
                  int createdBy, LocalDateTime publishedAt, LocalDateTime expiresAt, boolean active) {
        this.noticeId = noticeId;
        this.title = title;
        this.content = content;
        this.audience = audience;
        this.createdBy = createdBy;
        this.publishedAt = publishedAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public long getNoticeId() { return noticeId; }
    public void setNoticeId(long noticeId) { this.noticeId = noticeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Audience getAudience() { return audience; }
    public void setAudience(Audience audience) { this.audience = audience; }
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Display status only. Reader authorization and visibility are enforced by service/SQL. */
    public String publicationStatus(LocalDateTime now) {
        if (!active) return "Draft / Inactive";
        if (expiresAt != null && !expiresAt.isAfter(now)) return "Expired";
        if (publishedAt == null || publishedAt.isAfter(now)) return "Scheduled";
        return "Published";
    }
}
