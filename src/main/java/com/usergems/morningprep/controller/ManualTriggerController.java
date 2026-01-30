package com.usergems.morningprep.controller;

import com.usergems.morningprep.domain.entity.Email;
import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.domain.repository.EmailRepository;
import com.usergems.morningprep.domain.repository.UserRepository;
import com.usergems.morningprep.scheduler.MorningEmailScheduler;
import com.usergems.morningprep.service.CalendarSyncService;
import com.usergems.morningprep.service.EmailGenerationService;
import com.usergems.morningprep.service.EmailStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Controller for manual triggering of morning email generation.
 * Useful for testing and demos.
 */
@Slf4j
@RestController
@RequestMapping("/api/trigger")
@RequiredArgsConstructor
public class ManualTriggerController {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final CalendarSyncService calendarSyncService;
    private final EmailGenerationService emailGenerationService;
    private final EmailStorageService emailStorageService;

    /**
     * Trigger morning email for a specific user.
     * GET /api/trigger/user/{email}?date=2022-06-08 (optional date param)
     */
    @GetMapping("/user/{email}")
    public ResponseEntity<TriggerResponse> triggerForUser(
            @PathVariable String email,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Manual trigger requested for user: {} on date: {}", email, date);

        return userRepository.findByEmail(email)
            .map(user -> {
                try {
                    // Sync events
                    CalendarSyncService.SyncResult syncResult = calendarSyncService.syncEventsForUser(user);

                    // Generate email for specified date or today in user's timezone
                    LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of(user.getTimezone()));
                    EmailGenerationService.GeneratedEmail generated =
                        emailGenerationService.generateEmail(user, targetDate);

                    // Store email
                    emailStorageService.storeEmail(user, targetDate, generated);

                    return ResponseEntity.ok(new TriggerResponse(
                        "success",
                        String.format("Morning email generated for %s with %d meetings",
                            email, generated.meetingCount()),
                        generated.meetingCount(),
                        syncResult.newEvents(),
                        syncResult.updatedEvents()
                    ));
                } catch (Exception e) {
                    log.error("Failed to trigger email for {}", email, e);
                    return ResponseEntity.internalServerError().body(new TriggerResponse(
                        "error",
                        "Failed to generate email: " + e.getMessage(),
                        0, 0, 0
                    ));
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Trigger morning email for all users.
     * GET /api/trigger/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<TriggerResponse>> triggerForAll() {
        log.info("Manual trigger requested for all users");

        List<User> users = userRepository.findAll();
        List<TriggerResponse> results = users.stream()
            .map(user -> {
                try {
                    CalendarSyncService.SyncResult syncResult = calendarSyncService.syncEventsForUser(user);
                    LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
                    EmailGenerationService.GeneratedEmail generated =
                        emailGenerationService.generateEmail(user, today);
                    emailStorageService.storeEmail(user, today, generated);

                    return new TriggerResponse(
                        "success",
                        String.format("%s: %d meetings", user.getEmail(), generated.meetingCount()),
                        generated.meetingCount(),
                        syncResult.newEvents(),
                        syncResult.updatedEvents()
                    );
                } catch (Exception e) {
                    log.error("Failed for user {}", user.getEmail(), e);
                    return new TriggerResponse(
                        "error",
                        user.getEmail() + ": " + e.getMessage(),
                        0, 0, 0
                    );
                }
            })
            .toList();

        return ResponseEntity.ok(results);
    }

    /**
     * Get the generated email JSON for a user and date.
     * GET /api/trigger/email/{email}?date=2022-07-01
     */
    @GetMapping(value = "/email/{email}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getEmail(
            @PathVariable String email,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return userRepository.findByEmail(email)
            .flatMap(user -> {
                LocalDate targetDate = date != null ? date :
                    LocalDate.now(ZoneId.of(user.getTimezone()));
                return emailRepository.findByUserAndEmailDate(user, targetDate);
            })
            .map(e -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(e.getContentJson()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the generated email HTML for a user and date.
     * GET /api/trigger/email/{email}/html?date=2022-07-01
     */
    @GetMapping(value = "/email/{email}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getEmailHtml(
            @PathVariable String email,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return userRepository.findByEmail(email)
            .flatMap(user -> {
                LocalDate targetDate = date != null ? date :
                    LocalDate.now(ZoneId.of(user.getTimezone()));
                return emailRepository.findByUserAndEmailDate(user, targetDate);
            })
            .map(e -> ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(e.getContentHtml() != null ? e.getContentHtml() : "No HTML content"))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Sync calendar events for a user without generating email.
     * GET /api/trigger/sync/{email}
     */
    @GetMapping("/sync/{email}")
    public ResponseEntity<SyncResponse> syncUser(@PathVariable String email) {
        log.info("Sync requested for user: {}", email);

        return userRepository.findByEmail(email)
            .map(user -> {
                CalendarSyncService.SyncResult result = calendarSyncService.syncEventsForUser(user);
                return ResponseEntity.ok(new SyncResponse(
                    "success",
                    email,
                    result.newEvents(),
                    result.updatedEvents(),
                    result.pagesProcessed()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all users.
     * GET /api/trigger/users
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserInfo>> listUsers() {
        List<UserInfo> users = userRepository.findAll().stream()
            .map(u -> new UserInfo(u.getId(), u.getEmail(), u.getTimezone(), u.getLastSyncAt()))
            .toList();
        return ResponseEntity.ok(users);
    }

    record TriggerResponse(
        String status,
        String message,
        int meetingCount,
        int newEvents,
        int updatedEvents
    ) {}

    record SyncResponse(
        String status,
        String userEmail,
        int newEvents,
        int updatedEvents,
        int pagesProcessed
    ) {}

    record UserInfo(
        Long id,
        String email,
        String timezone,
        java.time.Instant lastSyncAt
    ) {}
}
