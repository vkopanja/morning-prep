package com.usergems.morningprep.scheduler;

import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.domain.repository.UserRepository;
import com.usergems.morningprep.service.CalendarSyncService;
import com.usergems.morningprep.service.EmailGenerationService;
import com.usergems.morningprep.service.EmailStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Scheduler for sending morning update emails at 8am per user timezone.
 * Runs every minute and checks if any timezone has reached the target hour.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class MorningEmailScheduler {

    private final UserRepository userRepository;
    private final CalendarSyncService calendarSyncService;
    private final EmailGenerationService emailGenerationService;
    private final EmailStorageService emailStorageService;
    private final int morningEmailHour;

    public MorningEmailScheduler(
            UserRepository userRepository,
            CalendarSyncService calendarSyncService,
            EmailGenerationService emailGenerationService,
            EmailStorageService emailStorageService,
            @Value("${scheduler.morning-email-hour:8}") int morningEmailHour) {
        this.userRepository = userRepository;
        this.calendarSyncService = calendarSyncService;
        this.emailGenerationService = emailGenerationService;
        this.emailStorageService = emailStorageService;
        this.morningEmailHour = morningEmailHour;
        log.info("Morning email scheduler initialized (target hour: {})", morningEmailHour);
    }

    /**
     * Run every minute to check if any timezone has reached the target hour.
     * This approach handles all timezones dynamically without needing
     * separate scheduled jobs for each timezone.
     */
    @Scheduled(cron = "0 * * * * *") // Every minute at :00 seconds
    public void checkAndSendMorningEmails() {
        LocalTime targetTime = LocalTime.of(morningEmailHour, 0);

        // Find all unique timezones used by users
        List<String> allTimezones = userRepository.findAllDistinctTimezones();

        if (allTimezones.isEmpty()) {
            return;
        }

        // Find timezones where it's currently the target hour
        List<String> targetTimezones = allTimezones.stream()
            .filter(tz -> isTargetTime(tz, targetTime))
            .toList();

        if (targetTimezones.isEmpty()) {
            return;
        }

        log.info("{}:00 reached in timezones: {}", morningEmailHour, targetTimezones);

        // Get users in those timezones
        List<User> users = userRepository.findByTimezoneIn(targetTimezones);

        log.info("Processing morning emails for {} users", users.size());

        // Process each user
        for (User user : users) {
            processUserSafely(user);
        }
    }

    /**
     * Manual trigger for a single user (used by API endpoint).
     */
    public ProcessingResult triggerForUser(User user) {
        log.info("Manual trigger for user: {}", user.getEmail());
        return processUser(user);
    }

    /**
     * Manual trigger for all users (used by API endpoint).
     */
    public List<ProcessingResult> triggerForAllUsers() {
        log.info("Manual trigger for all users");
        List<User> users = userRepository.findAll();
        return users.stream()
            .map(this::processUserSafely)
            .toList();
    }

    private ProcessingResult processUserSafely(User user) {
        try {
            return processUser(user);
        } catch (Exception e) {
            log.error("Failed to process morning email for {}", user.getEmail(), e);
            return new ProcessingResult(
                user.getEmail(),
                false,
                e.getMessage(),
                null,
                0
            );
        }
    }

    private ProcessingResult processUser(User user) {
        log.info("Processing morning email for: {}", user.getEmail());

        // Step 1: Sync calendar events
        CalendarSyncService.SyncResult syncResult = calendarSyncService.syncEventsForUser(user);
        log.debug("Synced {} new, {} updated events for {}",
            syncResult.newEvents(), syncResult.updatedEvents(), user.getEmail());

        // Step 2: Determine today's date in user's timezone
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));

        // Step 3: Generate email
        EmailGenerationService.GeneratedEmail generated =
            emailGenerationService.generateEmail(user, today);

        // Step 4: Store email (simulates sending)
        emailStorageService.storeEmail(user, today, generated);

        log.info("Morning email completed for {} with {} meetings",
            user.getEmail(), generated.meetingCount());

        return new ProcessingResult(
            user.getEmail(),
            true,
            null,
            syncResult,
            generated.meetingCount()
        );
    }

    private boolean isTargetTime(String timezone, LocalTime targetTime) {
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
            return now.getHour() == targetTime.getHour()
                && now.getMinute() == targetTime.getMinute();
        } catch (Exception e) {
            log.warn("Invalid timezone: {}", timezone);
            return false;
        }
    }

    /**
     * Result of processing a single user.
     */
    public record ProcessingResult(
        String userEmail,
        boolean success,
        String errorMessage,
        CalendarSyncService.SyncResult syncResult,
        int meetingCount
    ) {}
}
