package com.usergems.morningprep.domain.repository;

import com.usergems.morningprep.domain.entity.CalendarEvent;
import com.usergems.morningprep.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CalendarEvent entity operations.
 * Includes custom queries for sync optimization and meeting analytics.
 */
@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /**
     * Find an event by its external ID and user.
     * Used for upsert logic during calendar sync.
     */
    Optional<CalendarEvent> findByExternalIdAndUser(Long externalId, User user);

    /**
     * Get the most recent changed_at timestamp for a user's events.
     * Used for incremental sync optimization.
     */
    @Query("""
        SELECT MAX(ce.changedAt) FROM CalendarEvent ce
        WHERE ce.user = :user
        """)
    Optional<Instant> findLatestChangedAtByUser(@Param("user") User user);

    /**
     * Get today's events for a user within a time range.
     * Used for morning email generation.
     */
    @Query("""
        SELECT ce FROM CalendarEvent ce
        WHERE ce.user = :user
        AND ce.startTime >= :dayStart
        AND ce.startTime < :dayEnd
        ORDER BY ce.startTime ASC
        """)
    List<CalendarEvent> findEventsInRange(
        @Param("user") User user,
        @Param("dayStart") Instant dayStart,
        @Param("dayEnd") Instant dayEnd
    );

    /**
     * Count total meetings where a specific email appears as an attendee.
     * Used for "12th Meeting" counter in email.
     */
    @Query(value = """
        SELECT COUNT(*) FROM calendar_events
        WHERE :email = ANY(accepted_emails)
        OR :email = ANY(rejected_emails)
        """, nativeQuery = true)
    int countMeetingsWithEmail(@Param("email") String email);

    /**
     * Find colleagues who have previously met with an external attendee.
     * Returns colleague name and meeting count for "previouslyMetWith" field.
     */
    @Query(value = """
        SELECT u.email as colleague_email,
               SPLIT_PART(u.email, '@', 1) as colleague_name,
               COUNT(*) as times_met
        FROM calendar_events ce
        JOIN users u ON u.id = ce.user_id
        WHERE :externalEmail = ANY(ce.accepted_emails)
          AND u.email != :currentUserEmail
        GROUP BY u.email
        ORDER BY times_met DESC
        """, nativeQuery = true)
    List<ColleagueMeetingCount> findColleaguesWhoMetWith(
        @Param("externalEmail") String externalEmail,
        @Param("currentUserEmail") String currentUserEmail
    );

    /**
     * Projection interface for colleague meeting count results.
     */
    interface ColleagueMeetingCount {
        String getColleagueEmail();
        String getColleagueName();
        Integer getTimesMet();
    }

    /**
     * Delete all events for a user (for testing/reset purposes).
     */
    void deleteAllByUser(User user);
}
