package com.usergems.morningprep.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a generated morning update email.
 * Stores the structured JSON content and optional HTML rendering.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"user", "contentJson", "contentHtml"})
@Entity
@Table(name = "emails", indexes = {
    @Index(name = "idx_emails_user_date", columnList = "user_id, email_date")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "email_date"})
})
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email_date", nullable = false)
    private LocalDate emailDate;

    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "JSONB")
    private String contentJson;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "meeting_count")
    private Integer meetingCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Convenience constructor
    public Email(User user, LocalDate emailDate, String subject, String contentJson) {
        this.user = user;
        this.emailDate = emailDate;
        this.subject = subject;
        this.contentJson = contentJson;
    }

    // Helper methods
    public boolean isSent() {
        return sentAt != null;
    }

    public void markAsSent() {
        this.sentAt = Instant.now();
    }
}
