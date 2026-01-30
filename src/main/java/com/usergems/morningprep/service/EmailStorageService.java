package com.usergems.morningprep.service;

import com.usergems.morningprep.domain.entity.Email;
import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.domain.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Service for storing and managing generated emails.
 * Handles idempotency (prevents duplicate emails for same user/date).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailStorageService {

    private final EmailRepository emailRepository;

    /**
     * Store a generated email, preventing duplicates for same user/date.
     * Updates existing email if one exists for the same date.
     *
     * @param user           The user the email is for
     * @param date           The email date
     * @param generatedEmail The generated email content
     * @return The stored Email entity
     */
    @Transactional
    public Email storeEmail(
            User user,
            LocalDate date,
            EmailGenerationService.GeneratedEmail generatedEmail) {

        // Check for existing email (idempotency)
        Optional<Email> existing = emailRepository.findByUserAndEmailDate(user, date);

        if (existing.isPresent()) {
            log.info("Email already exists for {} on {}, updating", user.getEmail(), date);
            Email email = existing.get();
            email.setSubject(generatedEmail.subject());
            email.setContentJson(generatedEmail.contentJson());
            email.setContentHtml(generatedEmail.contentHtml());
            email.setMeetingCount(generatedEmail.meetingCount());
            email.setSentAt(Instant.now()); // Mark as "sent"
            return emailRepository.save(email);
        }

        Email email = new Email();
        email.setUser(user);
        email.setEmailDate(date);
        email.setSubject(generatedEmail.subject());
        email.setContentJson(generatedEmail.contentJson());
        email.setContentHtml(generatedEmail.contentHtml());
        email.setMeetingCount(generatedEmail.meetingCount());
        email.setSentAt(Instant.now()); // Mark as "sent"

        Email saved = emailRepository.save(email);
        log.info("Stored morning email for {} on {} with {} meetings",
            user.getEmail(), date, generatedEmail.meetingCount());

        return saved;
    }

    /**
     * Get email for a user and date.
     */
    @Transactional(readOnly = true)
    public Optional<Email> getEmail(User user, LocalDate date) {
        return emailRepository.findByUserAndEmailDate(user, date);
    }

    /**
     * Check if email already exists for a user and date.
     */
    @Transactional(readOnly = true)
    public boolean emailExists(User user, LocalDate date) {
        return emailRepository.existsByUserAndEmailDate(user, date);
    }

    /**
     * Get all emails for a user (most recent first).
     */
    @Transactional(readOnly = true)
    public java.util.List<Email> getEmailsForUser(User user) {
        return emailRepository.findByUserOrderByEmailDateDesc(user);
    }
}
