package com.usergems.morningprep.domain.repository;

import com.usergems.morningprep.domain.entity.Email;
import com.usergems.morningprep.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Email entity operations.
 */
@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

    /**
     * Find an email by user and date.
     * Used for idempotency check before generating new email.
     */
    Optional<Email> findByUserAndEmailDate(User user, LocalDate emailDate);

    /**
     * Check if an email already exists for a user on a specific date.
     */
    boolean existsByUserAndEmailDate(User user, LocalDate emailDate);

    /**
     * Find all unsent emails for a user.
     * Used for retry logic if sending fails.
     */
    List<Email> findByUserAndSentAtIsNull(User user);

    /**
     * Find all emails for a user, ordered by date descending.
     */
    List<Email> findByUserOrderByEmailDateDesc(User user);

    /**
     * Find emails by date range for a user.
     */
    List<Email> findByUserAndEmailDateBetween(User user, LocalDate startDate, LocalDate endDate);
}
