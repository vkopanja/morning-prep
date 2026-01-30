package com.usergems.morningprep.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usergems.morningprep.domain.dto.email.MeetingDto;
import com.usergems.morningprep.domain.dto.email.MorningUpdateEmail;
import com.usergems.morningprep.domain.entity.User;
import com.usergems.morningprep.exception.EmailGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for generating morning update email content.
 * Produces JSON (and optionally HTML) content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailGenerationService {

    private static final String DEFAULT_SUBJECT = "Your Morning Update";

    // Default avatar SVG (simple person silhouette) as data URI - quotes encoded as %27
    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 40 40%27%3E%3Ccircle cx=%2720%27 cy=%2720%27 r=%2720%27 fill=%27%23ddd%27/%3E%3Ccircle cx=%2720%27 cy=%2715%27 r=%277%27 fill=%27%23999%27/%3E%3Cpath d=%27M6 40 Q6 28 20 28 Q34 28 34 40%27 fill=%27%23999%27/%3E%3C/svg%3E";

    private final MeetingAggregationService meetingAggregationService;
    private final ObjectMapper objectMapper;

    /**
     * Generate the complete morning update email content.
     *
     * @param user The user to generate email for
     * @param date The date for the email
     * @return GeneratedEmail containing JSON and optional HTML
     */
    public GeneratedEmail generateEmail(User user, LocalDate date) {
        log.info("Generating morning email for {} on {}", user.getEmail(), date);

        List<MeetingDto> meetings = meetingAggregationService
            .aggregateMeetingsForDate(user, date);

        MorningUpdateEmail emailContent = new MorningUpdateEmail(
            user.getEmail(),
            DEFAULT_SUBJECT,
            date,
            meetings
        );

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(emailContent);

            // HTML generation can be added later
            String html = generateBasicHtml(emailContent);

            log.info("Generated email for {} with {} meetings",
                user.getEmail(), meetings.size());

            return new GeneratedEmail(
                emailContent.subject(),
                json,
                html,
                meetings.size()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize email content for {}", user.getEmail(), e);
            throw new EmailGenerationException("Failed to serialize email content", e);
        }
    }

    /**
     * Generate basic HTML representation of the email.
     * This is a simple placeholder - a full implementation would use templates.
     */
    private String generateBasicHtml(MorningUpdateEmail email) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>").append(email.subject()).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; }\n");
        html.append(".meeting { border: 1px solid #ddd; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n");
        html.append(".meeting-title { font-size: 18px; font-weight: bold; margin-bottom: 8px; }\n");
        html.append(".meeting-time { color: #666; margin-bottom: 12px; }\n");
        html.append(".attendee { display: flex; align-items: center; margin: 8px 0; }\n");
        html.append(".avatar { width: 40px; height: 40px; border-radius: 50%; margin-right: 12px; background: #ddd; }\n");
        html.append(".attendee-info { flex: 1; }\n");
        html.append(".attendee-name { font-weight: bold; }\n");
        html.append(".attendee-title { color: #666; font-size: 14px; }\n");
        html.append(".company { background: #f5f5f5; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<h1>").append(email.subject()).append("</h1>\n");
        html.append("<p>").append(email.date()).append(" - ").append(email.meetingCount()).append(" meetings</p>\n");

        if (!email.hasMeetings()) {
            html.append("<p>No meetings scheduled for today.</p>\n");
        } else {
            for (MeetingDto meeting : email.meetings()) {
                html.append("<div class=\"meeting\">\n");
                html.append("<div class=\"meeting-title\">").append(escapeHtml(meeting.title())).append("</div>\n");
                html.append("<div class=\"meeting-time\">")
                    .append(meeting.startTime()).append(" - ").append(meeting.endTime())
                    .append(" (").append(meeting.durationFormatted()).append(")")
                    .append("</div>\n");

                if (meeting.company() != null) {
                    html.append("<div class=\"company\">\n");
                    html.append("<strong>").append(escapeHtml(meeting.company().name())).append("</strong>");
                    if (meeting.company().employees() != null) {
                        html.append(" - ").append(meeting.company().employeesFormatted());
                    }
                    html.append("</div>\n");
                }

                if (meeting.hasExternalAttendees()) {
                    html.append("<h4>External Attendees</h4>\n");
                    for (var attendee : meeting.externalAttendees()) {
                        html.append("<div class=\"attendee\">\n");
                        String avatarSrc = attendee.avatarUrl() != null ? escapeHtml(attendee.avatarUrl()) : DEFAULT_AVATAR;
                        html.append("<img class=\"avatar\" src=\"").append(avatarSrc)
                            .append("\" onerror=\"this.onerror=null; this.src='").append(DEFAULT_AVATAR).append("';\" />\n");
                        html.append("<div class=\"attendee-info\">\n");
                        html.append("<div class=\"attendee-name\">").append(escapeHtml(attendee.fullName())).append("</div>\n");
                        if (attendee.title() != null) {
                            html.append("<div class=\"attendee-title\">").append(escapeHtml(attendee.title())).append("</div>\n");
                        }
                        html.append("<div class=\"attendee-title\">").append(attendee.meetingOrdinal());
                        if (!attendee.previouslyMetWith().isEmpty()) {
                            html.append(" | Met with: ").append(String.join(", ", attendee.previouslyMetWith()));
                        }
                        html.append("</div>\n");
                        html.append("</div>\n");
                        html.append("</div>\n");
                    }
                }

                if (!meeting.internalAttendees().isEmpty()) {
                    html.append("<h4>Internal Attendees</h4>\n");
                    html.append("<p>");
                    html.append(meeting.internalAttendees().stream()
                        .map(a -> escapeHtml(a.name()))
                        .collect(java.util.stream.Collectors.joining(", ")));
                    html.append("</p>\n");
                }

                html.append("</div>\n");
            }
        }

        html.append("</body>\n</html>");
        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    /**
     * Result of email generation.
     */
    public record GeneratedEmail(
        String subject,
        String contentJson,
        String contentHtml,
        int meetingCount
    ) {}
}
