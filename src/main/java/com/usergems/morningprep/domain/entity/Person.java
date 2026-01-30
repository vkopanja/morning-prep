package com.usergems.morningprep.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Cached person data from the enrichment API.
 * Implements a 30-day TTL to minimize paid API calls.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = {"id", "email"})
@Entity
@Table(name = "persons", indexes = {
    @Index(name = "idx_persons_email", columnList = "email"),
    @Index(name = "idx_persons_fetched_at", columnList = "fetched_at")
})
public class Person {

    private static final int CACHE_TTL_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    private String title;

    @Column(name = "linkedin_url", columnDefinition = "TEXT")
    private String linkedinUrl;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_linkedin_url", columnDefinition = "TEXT")
    private String companyLinkedinUrl;

    @Column(name = "company_employees")
    private Integer companyEmployees;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(name = "not_found")
    private boolean notFound = false;

    // Convenience constructor
    public Person(String email) {
        this.email = email;
    }

    // Helper methods

    /**
     * Check if the cached data is still valid (within 30-day TTL).
     * @return true if cache is valid, false if expired
     */
    public boolean isCacheValid() {
        return fetchedAt.isAfter(Instant.now().minus(CACHE_TTL_DAYS, ChronoUnit.DAYS));
    }

    /**
     * Get full name (firstName + lastName).
     * @return full name or email prefix if names are null
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return email != null ? email.split("@")[0] : "";
    }
}
