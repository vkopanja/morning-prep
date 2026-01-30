package com.usergems.morningprep.domain.dto.email;

import java.util.List;

/**
 * DTO representing an external (non-UserGems) attendee with enrichment data.
 */
public record ExternalAttendeeDto(
    String email,
    String firstName,
    String lastName,
    String title,
    String avatarUrl,
    String linkedinUrl,
    boolean accepted,
    int meetingCount,
    String meetingOrdinal,
    List<String> previouslyMetWith
) {
    /**
     * Get full name (firstName + lastName).
     */
    public String fullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return email != null ? email.split("@")[0] : "";
    }

    /**
     * Check if this attendee has person data (from enrichment API).
     */
    public boolean hasPersonData() {
        return firstName != null || lastName != null || title != null;
    }
}
