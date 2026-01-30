package com.usergems.morningprep.domain.dto.email;

/**
 * DTO representing company information in the email.
 */
public record CompanyDto(
    String name,
    String linkedinUrl,
    Integer employees
) {
    /**
     * Format employee count for display (e.g., "700 employees").
     */
    public String employeesFormatted() {
        if (employees == null) {
            return null;
        }
        return employees + " employees";
    }
}
