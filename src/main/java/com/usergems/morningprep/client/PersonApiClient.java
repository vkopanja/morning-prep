package com.usergems.morningprep.client;

import com.usergems.morningprep.domain.dto.api.PersonApiResponse;
import com.usergems.morningprep.exception.PersonApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Client for the UserGems Person enrichment API.
 * Fetches person data by email address.
 */
@Slf4j
@Component
public class PersonApiClient {

    private final RestClient restClient;
    private final String bearerToken;

    public PersonApiClient(
            RestClient personRestClient,
            @Value("${usergems.person-api.bearer-token}") String bearerToken) {
        this.restClient = personRestClient;
        this.bearerToken = bearerToken;
    }

    /**
     * Fetch person data by email address.
     *
     * @param email The person's email address
     * @return Optional<PersonApiResponse> - empty if person not found (404)
     * @throws PersonApiException on API errors (non-404)
     */
    public Optional<PersonApiResponse> fetchPerson(String email) {
        log.debug("Fetching person data for email: {}", email);

        try {
            PersonApiResponse response = restClient.get()
                .uri("/person/{email}", email)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, resp) -> {
                    // Don't throw exception for 404, we'll handle it as empty Optional
                    log.debug("Person not found for email: {}", email);
                })
                .body(PersonApiResponse.class);

            if (response != null) {
                log.debug("Found person: {} {} ({})",
                    response.firstName(),
                    response.lastName(),
                    response.hasCompany() ? response.company().name() : "no company");
            }

            return Optional.ofNullable(response);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info("Person not found for email: {}", email);
                return Optional.empty();
            }
            log.error("Person API error for {}: {}", email, e.getStatusCode(), e);
            throw new PersonApiException("Failed to fetch person data: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to fetch person data for {}", email, e);
            throw new PersonApiException("Failed to fetch person data", e);
        }
    }

    /**
     * Check if the API is reachable (for health checks).
     */
    public boolean isHealthy() {
        try {
            // Try to fetch a known test email or just check connection
            fetchPerson("health-check@test.com");
            return true;
        } catch (Exception e) {
            log.warn("Person API health check failed", e);
            return false;
        }
    }
}
