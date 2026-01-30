package com.usergems.morningprep.domain.repository;

import com.usergems.morningprep.domain.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Person entity operations.
 * Implements caching logic with 30-day TTL.
 */
@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Find a person by their email address.
     */
    Optional<Person> findByEmail(String email);

    /**
     * Find persons by email addresses that have valid cache entries.
     * A valid cache entry is one where fetchedAt is after the cutoff date.
     */
    @Query("""
        SELECT p FROM Person p
        WHERE p.email IN :emails
        AND p.fetchedAt > :cutoffDate
        """)
    List<Person> findValidCachedPersons(
        @Param("emails") List<String> emails,
        @Param("cutoffDate") Instant cutoffDate
    );

    /**
     * Find stale cache entries that need refresh.
     * Used for cache cleanup/maintenance.
     */
    @Query("SELECT p FROM Person p WHERE p.fetchedAt < :cutoffDate")
    List<Person> findStaleEntries(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find all persons with not_found = true.
     * These are emails that returned 404 from the API.
     */
    List<Person> findByNotFoundTrue();

    /**
     * Check if a person exists by email.
     */
    boolean existsByEmail(String email);
}
