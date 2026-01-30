package com.usergems.morningprep.domain.dto.email;

/**
 * DTO representing an internal (UserGems) attendee.
 */
public record InternalAttendeeDto(
    String name,
    String email
) {}
