package com.usergems.morningprep.service;

import com.usergems.morningprep.client.CalendarApiClient;
import com.usergems.morningprep.domain.dto.api.CalendarApiResponse;
import com.usergems.morningprep.domain.dto.api.CalendarEventDto;
import com.usergems.morningprep.domain.entity.CalendarEvent;
import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.domain.repository.CalendarEventRepository;
import com.usergems.morningprep.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for synchronizing calendar events from the external API.
 * Implements incremental sync to minimize API calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final CalendarApiClient calendarApiClient;
    private final CalendarEventRepository eventRepository;
    private final UserRepository userRepository;

    /**
     * Perform incremental sync for a user.
     * <p>
     * Algorithm:
     * 1. Fetch events sorted by changed date (API default)
     * 2. Stop fetching when we encounter events with changedAt <= lastSyncedChangedAt
     * 3. Upsert new/changed events to database
     *
     * @param user The user to sync events for
     * @return SyncResult with counts of new/updated events
     */
    @Transactional
    public SyncResult syncEventsForUser(User user) {
        log.info("Starting calendar sync for user: {}", user.getEmail());

        Optional<Instant> lastChangedAt = eventRepository.findLatestChangedAtByUser(user);
        log.debug("Last synced changedAt for {}: {}",
            user.getEmail(),
            lastChangedAt.orElse(null));

        int newEvents = 0;
        int updatedEvents = 0;
        int page = 1;
        boolean shouldContinue = true;

        while (shouldContinue) {
            CalendarApiResponse response = calendarApiClient.fetchEvents(
                user.getCalendarApiKey(), page
            );

            if (!response.hasData()) {
                break;
            }

            for (CalendarEventDto eventDto : response.data()) {
                Instant eventChangedAt = eventDto.changed()
                    .atZone(ZoneOffset.UTC)
                    .toInstant();

                // Stop if we've reached already-synced events
                // Events are sorted by changed date DESC, so once we find an
                // unchanged event, all subsequent events are also unchanged
                if (lastChangedAt.isPresent() &&
                    !eventChangedAt.isAfter(lastChangedAt.get())) {
                    log.debug("Reached already-synced event (changedAt: {}), stopping sync",
                        eventChangedAt);
                    shouldContinue = false;
                    break;
                }

                // Upsert event
                Optional<CalendarEvent> existing = eventRepository
                    .findByExternalIdAndUser(eventDto.id(), user);

                if (existing.isPresent()) {
                    updateEvent(existing.get(), eventDto);
                    updatedEvents++;
                } else {
                    createEvent(user, eventDto);
                    newEvents++;
                }
            }

            if (!response.hasMorePages()) {
                shouldContinue = false;
            }

            page++;
        }

        // Update user's last sync timestamp
        user.setLastSyncAt(Instant.now());
        userRepository.save(user);

        SyncResult result = new SyncResult(newEvents, updatedEvents, page - 1);
        log.info("Sync complete for {}: {} new, {} updated events (fetched {} pages)",
            user.getEmail(), newEvents, updatedEvents, result.pagesProcessed());

        return result;
    }

    /**
     * Sync events for all users.
     * Processes each user independently, continuing on individual failures.
     *
     * @return Map of user email to sync result (or error message)
     */
    @Transactional
    public Map<String, SyncResult> syncAllUsers() {
        List<User> users = userRepository.findAll();
        log.info("Starting sync for {} users", users.size());

        return users.stream()
            .collect(Collectors.toMap(
                User::getEmail,
                user -> {
                    try {
                        return syncEventsForUser(user);
                    } catch (Exception e) {
                        log.error("Failed to sync events for {}", user.getEmail(), e);
                        return new SyncResult(-1, -1, 0); // Error indicator
                    }
                }
            ));
    }

    /**
     * Force full sync for a user (ignores last sync timestamp).
     * Use sparingly as it fetches all events.
     *
     * @param user The user to sync
     * @return SyncResult
     */
    @Transactional
    public SyncResult fullSyncForUser(User user) {
        log.info("Starting full sync for user: {}", user.getEmail());

        // Delete existing events to avoid conflicts
        eventRepository.deleteAllByUser(user);

        int newEvents = 0;
        int page = 1;

        while (true) {
            CalendarApiResponse response = calendarApiClient.fetchEvents(
                user.getCalendarApiKey(), page
            );

            if (!response.hasData()) {
                break;
            }

            for (CalendarEventDto eventDto : response.data()) {
                createEvent(user, eventDto);
                newEvents++;
            }

            if (!response.hasMorePages()) {
                break;
            }

            page++;
        }

        user.setLastSyncAt(Instant.now());
        userRepository.save(user);

        log.info("Full sync complete for {}: {} events synced", user.getEmail(), newEvents);
        return new SyncResult(newEvents, 0, page);
    }

    private CalendarEvent createEvent(User user, CalendarEventDto dto) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setExternalId(dto.id());
        mapDtoToEvent(event, dto);
        return eventRepository.save(event);
    }

    private void updateEvent(CalendarEvent event, CalendarEventDto dto) {
        mapDtoToEvent(event, dto);
        event.setSyncedAt(Instant.now());
        eventRepository.save(event);
    }

    private void mapDtoToEvent(CalendarEvent event, CalendarEventDto dto) {
        event.setTitle(dto.title());
        event.setStartTime(dto.start().atZone(ZoneOffset.UTC).toInstant());
        event.setEndTime(dto.end().atZone(ZoneOffset.UTC).toInstant());
        event.setAcceptedEmails(dto.acceptedSafe());
        event.setRejectedEmails(dto.rejectedSafe());
        event.setChangedAt(dto.changed().atZone(ZoneOffset.UTC).toInstant());
    }

    /**
     * Result of a sync operation.
     */
    public record SyncResult(int newEvents, int updatedEvents, int pagesProcessed) {
        public boolean isSuccess() {
            return newEvents >= 0 && updatedEvents >= 0;
        }

        public int totalProcessed() {
            return newEvents + updatedEvents;
        }
    }
}
