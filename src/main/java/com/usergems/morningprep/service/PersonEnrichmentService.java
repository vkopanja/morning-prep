package com.usergems.morningprep.service;

import com.usergems.morningprep.client.PersonApiClient;
import com.usergems.morningprep.domain.dto.api.PersonApiResponse;
import com.usergems.morningprep.domain.entity.Person;
import com.usergems.morningprep.domain.repository.PersonRepository;
import com.usergems.morningprep.exception.PersonApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for enriching person data with 30-day caching.
 * Minimizes paid API calls by caching responses.
 */
@Slf4j
@Service
public class PersonEnrichmentService {

    private final PersonApiClient personApiClient;
    private final PersonRepository personRepository;
    private final Duration cacheTtl;

    public PersonEnrichmentService(
            PersonApiClient personApiClient,
            PersonRepository personRepository,
            @Value("${usergems.person-api.cache-ttl-days:30}") int cacheTtlDays) {
        this.personApiClient = personApiClient;
        this.personRepository = personRepository;
        this.cacheTtl = Duration.ofDays(cacheTtlDays);
    }

    /**
     * Get person data with caching.
     * Returns cached data if valid (within TTL), otherwise fetches from API.
     *
     * @param email The person's email address
     * @return Optional<Person> - empty if person not found
     */
    @Transactional
    public Optional<Person> getPersonByEmail(String email) {
        // Check cache first
        Optional<Person> cached = personRepository.findByEmail(email);

        if (cached.isPresent()) {
            Person person = cached.get();
            if (person.isCacheValid()) {
                log.debug("Cache HIT for person: {}", email);
                return person.isNotFound() ? Optional.empty() : Optional.of(person);
            }
            log.debug("Cache EXPIRED for person: {} (fetched at {})", email, person.getFetchedAt());
        } else {
            log.debug("Cache MISS for person: {}", email);
        }

        // Cache miss or expired - fetch from API
        return fetchAndCachePerson(email, cached.orElse(null));
    }

    /**
     * Batch fetch persons for efficiency.
     * First checks cache, then fetches missing persons from API.
     *
     * @param emails List of email addresses
     * @return Map of email to Person (only includes found persons)
     */
    @Transactional
    public Map<String, Person> getPersonsByEmails(List<String> emails) {
        if (emails.isEmpty()) {
            return Map.of();
        }

        log.debug("Batch fetching {} persons", emails.size());

        // Get valid cached entries
        Instant cutoffDate = Instant.now().minus(cacheTtl);
        List<Person> cachedPersons = personRepository.findValidCachedPersons(emails, cutoffDate);

        Map<String, Person> result = cachedPersons.stream()
            .filter(p -> !p.isNotFound())
            .collect(Collectors.toMap(Person::getEmail, p -> p, (a, b) -> a));

        // Also track cached 404s to avoid re-fetching
        Set<String> cached404s = cachedPersons.stream()
            .filter(Person::isNotFound)
            .map(Person::getEmail)
            .collect(Collectors.toSet());

        // Find emails not in valid cache
        Set<String> cachedEmails = new HashSet<>(result.keySet());
        cachedEmails.addAll(cached404s);

        List<String> uncachedEmails = emails.stream()
            .filter(email -> !cachedEmails.contains(email))
            .toList();

        log.debug("Cache: {} hits, {} 404s, {} misses",
            result.size(), cached404s.size(), uncachedEmails.size());

        // Fetch uncached persons from API
        for (String email : uncachedEmails) {
            try {
                Optional<Person> person = fetchAndCachePerson(email, null);
                person.ifPresent(p -> result.put(email, p));
            } catch (PersonApiException e) {
                log.warn("Failed to fetch person for {}, skipping", email);
                // Continue with other emails
            }
        }

        return result;
    }

    /**
     * Force refresh person data from API (ignores cache).
     */
    @Transactional
    public Optional<Person> refreshPerson(String email) {
        log.info("Force refreshing person: {}", email);
        Optional<Person> existing = personRepository.findByEmail(email);
        return fetchAndCachePerson(email, existing.orElse(null));
    }

    /**
     * Get cache statistics for monitoring.
     */
    public CacheStats getCacheStats() {
        long totalEntries = personRepository.count();
        Instant cutoffDate = Instant.now().minus(cacheTtl);
        long staleEntries = personRepository.findStaleEntries(cutoffDate).size();
        long notFoundEntries = personRepository.findByNotFoundTrue().size();

        return new CacheStats(totalEntries, staleEntries, notFoundEntries);
    }

    private Optional<Person> fetchAndCachePerson(String email, Person existing) {
        try {
            Optional<PersonApiResponse> apiResponse = personApiClient.fetchPerson(email);

            Person person = existing != null ? existing : new Person(email);

            if (apiResponse.isPresent()) {
                mapApiResponseToPerson(person, apiResponse.get());
                person.setNotFound(false);
                person.setFetchedAt(Instant.now());
                personRepository.save(person);
                log.debug("Cached person data for: {}", email);
                return Optional.of(person);
            } else {
                // Store 404 response to avoid retrying
                person.setEmail(email);
                person.setNotFound(true);
                person.setFetchedAt(Instant.now());
                // Clear any old data
                person.setFirstName(null);
                person.setLastName(null);
                person.setTitle(null);
                person.setAvatarUrl(null);
                person.setLinkedinUrl(null);
                person.setCompanyName(null);
                person.setCompanyLinkedinUrl(null);
                person.setCompanyEmployees(null);
                personRepository.save(person);
                log.debug("Cached 404 for person: {}", email);
                return Optional.empty();
            }
        } catch (PersonApiException e) {
            log.error("Failed to fetch person data for {}", email, e);
            // Return stale cache if available, otherwise empty
            if (existing != null && !existing.isNotFound()) {
                log.info("Returning stale cache for {} due to API error", email);
                return Optional.of(existing);
            }
            throw e;
        }
    }

    private void mapApiResponseToPerson(Person person, PersonApiResponse response) {
        person.setFirstName(response.firstName());
        person.setLastName(response.lastName());
        person.setAvatarUrl(response.avatar());
        person.setTitle(response.title());
        person.setLinkedinUrl(response.linkedinUrl());

        if (response.company() != null) {
            person.setCompanyName(response.company().name());
            person.setCompanyLinkedinUrl(response.company().linkedinUrl());
            person.setCompanyEmployees(response.company().employees());
        }
    }

    /**
     * Cache statistics for monitoring.
     */
    public record CacheStats(long totalEntries, long staleEntries, long notFoundEntries) {
        public long validEntries() {
            return totalEntries - staleEntries - notFoundEntries;
        }

        public double hitRate() {
            return totalEntries > 0 ? (double) validEntries() / totalEntries : 0;
        }
    }
}
