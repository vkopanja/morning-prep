package com.usergems.morningprep.domain.dto.email;

import java.time.LocalDate;
import java.util.List;

/**
 * Root DTO representing the complete morning update email content.
 */
public record MorningUpdateEmail(
    String recipient,
    String subject,
    LocalDate date,
    List<MeetingDto> meetings
) {
    /**
     * Get total meeting count.
     */
    public int meetingCount() {
        return meetings != null ? meetings.size() : 0;
    }

    /**
     * Check if there are any meetings.
     */
    public boolean hasMeetings() {
        return meetings != null && !meetings.isEmpty();
    }

    /**
     * Get count of meetings with external attendees.
     */
    public long meetingsWithExternalAttendees() {
        if (meetings == null) return 0;
        return meetings.stream()
            .filter(MeetingDto::hasExternalAttendees)
            .count();
    }
}
