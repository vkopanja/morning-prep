package com.usergems.morningprep.domain.dto.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO representing the paginated response from the Calendar API.
 */
public record CalendarApiResponse(
    int total,

    @JsonProperty("per_page")
    int perPage,

    @JsonProperty("current_page")
    int currentPage,

    List<CalendarEventDto> data
) {
    /**
     * Check if there are more pages to fetch.
     */
    public boolean hasMorePages() {
        return currentPage * perPage < total;
    }

    /**
     * Calculate total number of pages.
     */
    public int totalPages() {
        return (int) Math.ceil((double) total / perPage);
    }

    /**
     * Check if response has any data.
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }
}
