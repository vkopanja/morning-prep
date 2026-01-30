package com.usergems.morningprep.domain.dto.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a single calendar event from the external Calendar API.
 */
public record CalendarEventDto(
    long id,

    @JsonProperty("changed")
    @JsonFormat(pattern = "yyyy-M-d H:mm:ss")
    LocalDateTime changed,

    @JsonProperty("start")
    @JsonFormat(pattern = "yyyy-M-d H:mm:ss")
    LocalDateTime start,

    @JsonProperty("end")
    @JsonFormat(pattern = "yyyy-M-d H:mm:ss")
    LocalDateTime end,

    String title,

    List<String> accepted,

    List<String> rejected
) {
    /**
     * Get accepted emails with null safety.
     */
    public List<String> acceptedSafe() {
        return accepted != null ? accepted : List.of();
    }

    /**
     * Get rejected emails with null safety.
     */
    public List<String> rejectedSafe() {
        return rejected != null ? rejected : List.of();
    }
}
