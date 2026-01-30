package com.usergems.morningprep.service;

import com.usergems.morningprep.domain.dto.email.*;
import com.usergems.morningprep.domain.entity.CalendarEvent;
import com.usergems.morningprep.domain.entity.Person;
import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.domain.repository.CalendarEventRepository;
import com.usergems.morningprep.util.OrdinalUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for aggregating meeting data with enriched attendee information.
 * This is the core business logic for building the morning email content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAggregationService {

    private static final String USERGEMS_DOMAIN = "@usergems.com";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    private final CalendarEventRepository eventRepository;
    private final PersonEnrichmentService personEnrichmentService;

    /**
     * Aggregate all meeting data for a user's daily email.
     *
     * @param user The user to generate meeting data for
     * @param date The date to fetch meetings for
     * @return List of aggregated meeting DTOs
     */
    @Transactional  // Not readOnly - delegates to PersonEnrichmentService which may write
    public List<MeetingDto> aggregateMeetingsForDate(User user, LocalDate date) {
        ZoneId userZone = ZoneId.of(user.getTimezone());
        Instant dayStart = date.atStartOfDay(userZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();

        log.debug("Aggregating meetings for {} on {} (zone: {}, range: {} to {})",
            user.getEmail(), date, userZone, dayStart, dayEnd);

        List<CalendarEvent> events = eventRepository.findEventsInRange(user, dayStart, dayEnd);

        if (events.isEmpty()) {
            log.info("No meetings found for {} on {}", user.getEmail(), date);
            return List.of();
        }

        log.info("Found {} meetings for {} on {}", events.size(), user.getEmail(), date);

        // Collect all external emails for batch enrichment
        Set<String> externalEmails = events.stream()
            .flatMap(e -> Stream.concat(
                e.getAcceptedEmails().stream(),
                e.getRejectedEmails().stream()
            ))
            .filter(email -> !isInternalEmail(email))
            .collect(Collectors.toSet());

        log.debug("Batch fetching {} external attendees", externalEmails.size());

        // Batch fetch person data
        Map<String, Person> personData = personEnrichmentService
            .getPersonsByEmails(new ArrayList<>(externalEmails));

        log.debug("Enriched {} out of {} external attendees",
            personData.size(), externalEmails.size());

        // Map events to DTOs
        return events.stream()
            .map(event -> buildMeetingDto(event, user, personData))
            .toList();
    }

    private MeetingDto buildMeetingDto(
            CalendarEvent event,
            User user,
            Map<String, Person> personData) {

        ZoneId userZone = ZoneId.of(user.getTimezone());
        ZonedDateTime start = event.getStartTime().atZone(userZone);
        ZonedDateTime end = event.getEndTime().atZone(userZone);

        // Calculate duration
        int durationMinutes = (int) Duration.between(start, end).toMinutes();

        // Separate internal and external attendees
        List<InternalAttendeeDto> internalAttendees = buildInternalAttendees(event, user);
        List<ExternalAttendeeDto> externalAttendees = buildExternalAttendees(event, user, personData);

        // Determine company info from first external attendee with data
        CompanyDto company = externalAttendees.stream()
            .map(a -> personData.get(a.email()))
            .filter(Objects::nonNull)
            .filter(p -> p.getCompanyName() != null)
            .findFirst()
            .map(p -> new CompanyDto(
                p.getCompanyName(),
                p.getCompanyLinkedinUrl(),
                p.getCompanyEmployees()
            ))
            .orElse(null);

        return new MeetingDto(
            event.getTitle(),
            start.format(TIME_FORMATTER),
            end.format(TIME_FORMATTER),
            durationMinutes,
            company,
            internalAttendees,
            externalAttendees
        );
    }

    private List<InternalAttendeeDto> buildInternalAttendees(CalendarEvent event, User user) {
        return event.getAllAttendeeEmails().stream()
            .filter(this::isInternalEmail)
            .filter(email -> !email.equalsIgnoreCase(user.getEmail())) // Exclude the user themselves
            .distinct()
            .map(this::buildInternalAttendee)
            .toList();
    }

    private InternalAttendeeDto buildInternalAttendee(String email) {
        String name = email.split("@")[0];
        // Capitalize first letter
        name = capitalize(name);
        return new InternalAttendeeDto(name, email);
    }

    private List<ExternalAttendeeDto> buildExternalAttendees(
            CalendarEvent event,
            User user,
            Map<String, Person> personData) {

        List<ExternalAttendeeDto> attendees = new ArrayList<>();

        // Process accepted external attendees
        for (String email : event.getAcceptedEmails()) {
            if (!isInternalEmail(email)) {
                attendees.add(buildExternalAttendee(email, true, user.getEmail(), personData));
            }
        }

        // Process rejected external attendees
        for (String email : event.getRejectedEmails()) {
            if (!isInternalEmail(email)) {
                attendees.add(buildExternalAttendee(email, false, user.getEmail(), personData));
            }
        }

        return attendees;
    }

    private ExternalAttendeeDto buildExternalAttendee(
            String email,
            boolean accepted,
            String currentUserEmail,
            Map<String, Person> personData) {

        Person person = personData.get(email);

        // Get meeting count
        int meetingCount = eventRepository.countMeetingsWithEmail(email);
        String meetingOrdinal = OrdinalUtils.toOrdinalMeeting(meetingCount);

        // Get colleagues who have met with this person
        List<CalendarEventRepository.ColleagueMeetingCount> colleagues =
            eventRepository.findColleaguesWhoMetWith(email, currentUserEmail);

        List<String> previouslyMetWith = colleagues.stream()
            .map(c -> String.format("%s (%dx)",
                capitalize(c.getColleagueName()),
                c.getTimesMet()))
            .toList();

        return new ExternalAttendeeDto(
            email,
            person != null ? person.getFirstName() : extractNameFromEmail(email),
            person != null ? person.getLastName() : null,
            person != null ? person.getTitle() : null,
            person != null ? person.getAvatarUrl() : null,
            person != null ? person.getLinkedinUrl() : null,
            accepted,
            meetingCount,
            meetingOrdinal,
            previouslyMetWith
        );
    }

    private boolean isInternalEmail(String email) {
        return email != null && email.toLowerCase().endsWith(USERGEMS_DOMAIN);
    }

    private String extractNameFromEmail(String email) {
        if (email == null) return "";
        String localPart = email.split("@")[0];
        // Handle names like "john.doe" or "john_doe"
        String[] parts = localPart.split("[._]");
        return capitalize(parts[0]);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
