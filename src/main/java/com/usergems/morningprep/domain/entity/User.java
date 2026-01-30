package com.usergems.morningprep.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sales representative user who receives morning update emails.
 * Each user has their own calendar API key and timezone preference.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = {"id", "email"})
@ToString(exclude = {"calendarEvents", "emails"})
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_timezone", columnList = "timezone")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "calendar_api_key", nullable = false)
    private String calendarApiKey;

    private String timezone = "UTC";

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalendarEvent> calendarEvents = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Email> emails = new ArrayList<>();

    // Convenience constructor
    public User(String email, String calendarApiKey, String timezone) {
        this.email = email;
        this.calendarApiKey = calendarApiKey;
        this.timezone = timezone;
    }

    // Helper methods
    public void addCalendarEvent(CalendarEvent event) {
        calendarEvents.add(event);
        event.setUser(this);
    }

    public void addEmail(Email email) {
        emails.add(email);
        email.setUser(this);
    }
}
