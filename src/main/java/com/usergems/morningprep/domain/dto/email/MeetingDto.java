package com.usergems.morningprep.domain.dto.email;

import java.util.List;

/**
 * DTO representing a single meeting with all attendee information.
 */
public record MeetingDto(
    String title,
    String startTime,
    String endTime,
    int durationMinutes,
    CompanyDto company,
    List<InternalAttendeeDto> internalAttendees,
    List<ExternalAttendeeDto> externalAttendees
) {
    /**
     * Get total attendee count (internal + external).
     */
    public int totalAttendees() {
        return (internalAttendees != null ? internalAttendees.size() : 0) +
               (externalAttendees != null ? externalAttendees.size() : 0);
    }

    /**
     * Check if meeting has any external attendees.
     */
    public boolean hasExternalAttendees() {
        return externalAttendees != null && !externalAttendees.isEmpty();
    }

    /**
     * Format duration for display (e.g., "30 min" or "1h 30min").
     */
    public String durationFormatted() {
        if (durationMinutes < 60) {
            return durationMinutes + " min";
        }
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        if (minutes == 0) {
            return hours + "h";
        }
        return hours + "h " + minutes + "min";
    }
}
