package com.usergems.morningprep.domain.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a calendar event synced from the external Calendar API.
 * Uses PostgreSQL TEXT[] arrays for storing attendee emails.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = {"id", "externalId"})
@ToString(exclude = "user")
@Entity
@Table(name = "calendar_events", indexes = {
    @Index(name = "idx_events_user_start", columnList = "user_id, start_time"),
    @Index(name = "idx_events_changed_at", columnList = "changed_at"),
    @Index(name = "idx_events_external_user", columnList = "external_id, user_id")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"external_id", "user_id"})
})
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Type(ListArrayType.class)
    @Column(name = "accepted_emails", columnDefinition = "TEXT[]")
    private List<String> acceptedEmails = new ArrayList<>();

    @Type(ListArrayType.class)
    @Column(name = "rejected_emails", columnDefinition = "TEXT[]")
    private List<String> rejectedEmails = new ArrayList<>();

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    // Convenience constructor
    public CalendarEvent(Long externalId, User user, String title,
                         Instant startTime, Instant endTime, Instant changedAt) {
        this.externalId = externalId;
        this.user = user;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.changedAt = changedAt;
    }

    // Custom setters that handle null safely (override Lombok-generated ones)
    public void setAcceptedEmails(List<String> acceptedEmails) {
        this.acceptedEmails = acceptedEmails != null ? acceptedEmails : new ArrayList<>();
    }

    public void setRejectedEmails(List<String> rejectedEmails) {
        this.rejectedEmails = rejectedEmails != null ? rejectedEmails : new ArrayList<>();
    }

    // Helper method
    public List<String> getAllAttendeeEmails() {
        List<String> all = new ArrayList<>(acceptedEmails);
        all.addAll(rejectedEmails);
        return all;
    }
}
