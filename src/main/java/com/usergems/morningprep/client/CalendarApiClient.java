package com.usergems.morningprep.client;

import com.usergems.morningprep.domain.dto.api.CalendarApiResponse;
import com.usergems.morningprep.domain.dto.api.CalendarEventDto;
import com.usergems.morningprep.exception.CalendarApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Client for the UserGems Calendar API.
 * Handles pagination and provides methods for fetching calendar events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarApiClient {

    private final RestClient calendarRestClient;

    /**
     * Fetch a single page of calendar events for a user.
     *
     * @param apiKey The user's calendar API key (used as Bearer token)
     * @param page   The page number (1-indexed)
     * @return CalendarApiResponse containing events and pagination info
     * @throws CalendarApiException on API errors
     */
    public CalendarApiResponse fetchEvents(String apiKey, int page) {
        log.debug("Fetching calendar events page {}", page);

        try {
            CalendarApiResponse response = calendarRestClient.get()
                .uri("/events?page={page}", page)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    log.error("Calendar API client error: {}", resp.getStatusCode());
                    throw new CalendarApiException("Client error: " + resp.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    log.error("Calendar API server error: {}", resp.getStatusCode());
                    throw new CalendarApiException("Server error: " + resp.getStatusCode());
                })
                .body(CalendarApiResponse.class);

            if (response == null) {
                throw new CalendarApiException("Received null response from Calendar API");
            }

            log.debug("Fetched {} events from page {} (total: {})",
                response.data() != null ? response.data().size() : 0,
                page,
                response.total());

            return response;
        } catch (RestClientException e) {
            log.error("Failed to fetch calendar events page {}", page, e);
            throw new CalendarApiException("Failed to fetch calendar events", e);
        }
    }

    /**
     * Fetch all calendar events across all pages.
     * Use with caution - prefer incremental sync for efficiency.
     *
     * @param apiKey The user's calendar API key
     * @return List of all calendar events
     */
    public List<CalendarEventDto> fetchAllEvents(String apiKey) {
        List<CalendarEventDto> allEvents = new ArrayList<>();
        int page = 1;

        while (true) {
            CalendarApiResponse response = fetchEvents(apiKey, page);

            if (!response.hasData()) {
                break;
            }

            allEvents.addAll(response.data());

            if (!response.hasMorePages()) {
                break;
            }

            page++;
        }

        log.info("Fetched {} total events across {} pages", allEvents.size(), page);
        return allEvents;
    }

    /**
     * Stream calendar events page by page.
     * Useful for lazy iteration over large datasets.
     *
     * @param apiKey The user's calendar API key
     * @return Stream of calendar events
     */
    public Stream<CalendarEventDto> streamEvents(String apiKey) {
        return Stream.iterate(1, page -> page + 1)
            .map(page -> fetchEvents(apiKey, page))
            .takeWhile(CalendarApiResponse::hasData)
            .flatMap(response -> response.data().stream());
    }
}
