package com.usergems.morningprep.domain.dto.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing the response from the Person enrichment API.
 */
public record PersonApiResponse(
    @JsonProperty("first_name")
    String firstName,

    @JsonProperty("last_name")
    String lastName,

    String avatar,

    String title,

    @JsonProperty("linkedin_url")
    String linkedinUrl,

    CompanyInfo company
) {
    /**
     * Nested DTO for company information.
     */
    public record CompanyInfo(
        String name,

        @JsonProperty("linkedin_url")
        String linkedinUrl,

        int employees
    ) {}

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
        return "";
    }

    /**
     * Check if company information is available.
     */
    public boolean hasCompany() {
        return company != null && company.name() != null;
    }
}
