# Morning Prep - Architecture & Component Guide

## Overview

**Morning Prep** is a Spring Boot 4.0.1 application that generates personalized "Morning Update" emails for sales representatives. It enriches calendar meetings with attendee information from external APIs and delivers a consolidated summary at 8am daily in each user's timezone.

### Tech Stack
- **Framework**: Spring Boot 4.0.1 (released Nov 2025)
- **Language**: Java 21 (LTS)
- **Database**: PostgreSQL 16/17
- **ORM**: Hibernate 7 with Spring Data JPA
- **Migrations**: Flyway 11.x
- **Build**: Gradle 8.14 (Kotlin DSL)
- **Containerization**: Docker & docker-compose

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Morning Prep Application                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐    │
│  │  Scheduler   │───▶│  Orchestration   │───▶│  Email Generation  │    │
│  │  (8am/tz)    │    │                  │    │                    │    │
│  └──────────────┘    └──────────────────┘    └────────────────────┘    │
│         │                    │                        │                 │
│         ▼                    ▼                        ▼                 │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐    │
│  │  Calendar    │    │  Person          │    │  Meeting           │    │
│  │  Sync        │    │  Enrichment      │    │  Aggregation       │    │
│  │  Service     │    │  Service         │    │  Service           │    │
│  └──────────────┘    └──────────────────┘    └────────────────────┘    │
│         │                    │                        │                 │
│         ▼                    ▼                        ▼                 │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐    │
│  │  Calendar    │    │  Person API      │    │  Email Storage     │    │
│  │  API Client  │    │  Client          │    │  Service           │    │
│  └──────────────┘    └──────────────────┘    └────────────────────┘    │
│         │                    │                        │                 │
└─────────┼────────────────────┼────────────────────────┼─────────────────┘
          │                    │                        │
          ▼                    ▼                        ▼
   ┌─────────────┐      ┌─────────────┐         ┌─────────────┐
   │  External   │      │  External   │         │  PostgreSQL │
   │  Calendar   │      │  Person     │         │  Database   │
   │  API        │      │  API        │         │             │
   └─────────────┘      └─────────────┘         └─────────────┘
```

---

## Project Structure

```
morning-prep/
├── src/main/java/com/usergems/morningprep/
│   ├── MorningPrepApplication.java      # Main entry point
│   │
│   ├── config/                          # Configuration classes
│   │   ├── JacksonConfig.java           # JSON serialization config
│   │   ├── RestClientConfig.java        # HTTP client configuration
│   │   └── SchedulerConfig.java         # Scheduling configuration
│   │
│   ├── domain/                          # Domain model
│   │   ├── entity/                      # JPA entities
│   │   │   ├── User.java                # Sales rep user
│   │   │   ├── CalendarEvent.java       # Synced calendar event
│   │   │   ├── Person.java              # Cached person data
│   │   │   └── Email.java               # Generated email
│   │   │
│   │   ├── repository/                  # Spring Data repositories
│   │   │   ├── UserRepository.java
│   │   │   ├── CalendarEventRepository.java
│   │   │   ├── PersonRepository.java
│   │   │   └── EmailRepository.java
│   │   │
│   │   └── dto/                         # Data Transfer Objects
│   │       ├── api/                     # External API DTOs
│   │       │   ├── CalendarApiResponse.java
│   │       │   ├── CalendarEventDto.java
│   │       │   └── PersonApiResponse.java
│   │       │
│   │       └── email/                   # Email content DTOs
│   │           ├── MorningUpdateEmail.java
│   │           ├── MeetingDto.java
│   │           ├── ExternalAttendeeDto.java
│   │           ├── InternalAttendeeDto.java
│   │           └── CompanyDto.java
│   │
│   ├── client/                          # External API clients
│   │   ├── CalendarApiClient.java       # Calendar API integration
│   │   └── PersonApiClient.java         # Person enrichment API
│   │
│   ├── service/                         # Business logic
│   │   ├── CalendarSyncService.java     # Incremental calendar sync
│   │   ├── PersonEnrichmentService.java # Person data caching
│   │   ├── MeetingAggregationService.java # Core aggregation logic
│   │   ├── EmailGenerationService.java  # JSON/HTML generation
│   │   └── EmailStorageService.java     # Email persistence
│   │
│   ├── scheduler/                       # Scheduled tasks
│   │   └── MorningEmailScheduler.java   # 8am timezone-aware scheduler
│   │
│   ├── controller/                      # REST endpoints
│   │   └── ManualTriggerController.java # Manual trigger & retrieval
│   │
│   ├── exception/                       # Custom exceptions
│   │   ├── CalendarApiException.java
│   │   ├── PersonApiException.java
│   │   └── EmailGenerationException.java
│   │
│   └── util/                            # Utilities
│       └── OrdinalUtils.java            # "1st", "2nd", "3rd" formatting
│
├── src/main/resources/
│   ├── application.yml                  # Base configuration
│   ├── application-local.yml            # Local development
│   ├── application-docker.yml           # Docker deployment
│   └── db/migration/
│       ├── V1__initial_schema.sql       # Database schema
│       └── V2__seed_users.sql           # Test users seed data
│
├── build.gradle.kts                     # Gradle build configuration
├── Dockerfile                           # Multi-stage Docker build
├── docker-compose.yml                   # PostgreSQL + API orchestration
└── ARCHITECTURE.md                      # This file
```

---

## Component Deep Dive

### 1. Entities (`domain/entity/`)

#### User.java
Represents a sales representative who receives morning emails.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `email` | String | User's email (unique) |
| `calendarApiKey` | String | API key for calendar access |
| `timezone` | String | IANA timezone (e.g., "America/Los_Angeles") |
| `lastSyncAt` | Instant | Last calendar sync timestamp |
| `createdAt` | Instant | Account creation time |

**Relationships:**
- One-to-Many → `CalendarEvent` (user's synced events)
- One-to-Many → `Email` (user's generated emails)

#### CalendarEvent.java
Stores synced calendar events from the external API.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `externalId` | Long | ID from external calendar API |
| `user` | User | Owner of the event |
| `title` | String | Meeting title |
| `startTime` | Instant | Start timestamp (UTC) |
| `endTime` | Instant | End timestamp (UTC) |
| `acceptedEmails` | List<String> | PostgreSQL TEXT[] - accepted attendees |
| `rejectedEmails` | List<String> | PostgreSQL TEXT[] - declined attendees |
| `changedAt` | Instant | Last modification time (for incremental sync) |
| `syncedAt` | Instant | When we synced this event |

**Key Feature:** Uses `io.hypersistence:hypersistence-utils-hibernate-70` for PostgreSQL array support with Hibernate 7.

#### Person.java
Cached person data from the enrichment API with 30-day TTL.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `email` | String | Person's email (unique) |
| `firstName` | String | First name |
| `lastName` | String | Last name |
| `avatarUrl` | String | Profile picture URL |
| `title` | String | Job title |
| `linkedinUrl` | String | LinkedIn profile URL |
| `companyName` | String | Company name |
| `companyLinkedinUrl` | String | Company LinkedIn URL |
| `companyEmployees` | Integer | Company size |
| `fetchedAt` | Instant | When data was fetched |
| `notFound` | boolean | True if person API returned 404 |

**Key Methods:**
- `isCacheValid()` - Checks if within 30-day TTL
- `getFullName()` - Returns "First Last" or email prefix if unknown

#### Email.java
Stores generated morning update emails.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `user` | User | Recipient |
| `emailDate` | LocalDate | Date this email is for |
| `subject` | String | Email subject line |
| `contentJson` | String | JSONB - structured email data |
| `contentHtml` | String | TEXT - rendered HTML (nullable) |
| `meetingCount` | Integer | Number of meetings |
| `sentAt` | Instant | When email was sent (nullable) |
| `createdAt` | Instant | Generation timestamp |

**Unique Constraint:** `(user_id, email_date)` - one email per user per day

---

### 2. API Clients (`client/`)

#### CalendarApiClient.java
Fetches calendar events from the external API.

**Endpoint:** `GET /events?apiKey={key}&page={page}`

**Features:**
- Paginated fetching (10 events per page)
- Events sorted by `changed` date DESC (newest first)
- Configurable timeout (default 30s)

**Response Parsing:**
```java
CalendarApiResponse {
    int totalCount;
    int page;
    int perPage;
    List<CalendarEventDto> data;
}
```

**Date Format Quirk:** API returns dates like `"2022-6-8 9:30:00"` (single-digit day/month), so we use pattern `yyyy-M-d H:mm:ss`.

#### PersonApiClient.java
Fetches enriched person data from the Person API.

**Endpoint:** `GET /person?email={email}`
**Authentication:** Bearer token in header

**Features:**
- Returns person details including company info
- 404 handling for unknown emails
- Configurable timeout (default 30s)

---

### 3. Services (`service/`)

#### CalendarSyncService.java
**Purpose:** Incrementally sync calendar events from external API.

**Algorithm:**
```
1. Get last synced event's `changedAt` timestamp
2. Fetch page 1 of events (sorted by changed DESC)
3. For each event:
   - If changedAt <= lastSynced → STOP (already have it)
   - Otherwise → Upsert to database
4. Continue to next page if needed
5. Update user's lastSyncAt
```

**Why Incremental?**
- Minimizes API calls (stops when reaching known events)
- Handles updates to existing events
- Efficient for large calendars

**Key Methods:**
- `syncEventsForUser(User)` → Incremental sync
- `fullSyncForUser(User)` → Complete re-sync
- `syncAllUsers()` → Batch sync all users

#### PersonEnrichmentService.java
**Purpose:** Fetch and cache person data with 30-day TTL.

**Caching Strategy:**
```
1. Check database for existing person
2. If found AND fetchedAt within 30 days → Return cached
3. If found AND notFound=true → Skip (avoid re-fetching 404s)
4. Otherwise → Fetch from API and cache
```

**Batch Optimization:**
- `getPersonsByEmails(List<String>)` - Fetches multiple in one DB query
- Only calls API for cache misses
- Logs cache hit/miss statistics

#### MeetingAggregationService.java
**Purpose:** Core business logic that assembles meeting data for emails.

**What It Does:**
1. Fetches events for a specific date in user's timezone
2. Separates internal (@usergems.com) vs external attendees
3. Batch-fetches person data for external attendees
4. Calculates meeting count per external attendee (across all time)
5. Identifies which colleagues previously met with each attendee
6. Builds `MeetingDto` objects with all enriched data

**Key Logic - Meeting Count:**
```java
// Count all meetings where this external attendee participated
int meetingCount = countMeetingsWithAttendee(user, attendeeEmail);
String ordinal = OrdinalUtils.toOrdinal(meetingCount); // "1st", "2nd", etc.
```

**Key Logic - Previously Met With:**
```java
// Find internal colleagues who met this person before
List<String> previouslyMetWith = findColleaguesWhoMet(user, attendeeEmail, currentEventId);
```

#### EmailGenerationService.java
**Purpose:** Generate JSON and HTML email content.

**Output - JSON Structure:**
```json
{
  "recipient": "stephan@usergems.com",
  "subject": "Your Morning Update",
  "date": "2022-06-08",
  "meetings": [
    {
      "title": "UserGems x Algolia",
      "startTime": "02:30 am",
      "endTime": "04:00 am",
      "durationMinutes": 90,
      "company": {
        "name": "Algolia",
        "linkedinUrl": "https://linkedin.com/company/algolia",
        "employees": 700
      },
      "internalAttendees": [
        { "name": "Stephan", "email": "stephan@usergems.com" }
      ],
      "externalAttendees": [
        {
          "email": "joshua@algolia.com",
          "firstName": "Joshua",
          "lastName": "Mateer",
          "title": "Sr Manager, Marketing Operations",
          "avatarUrl": "https://...",
          "linkedinUrl": "https://linkedin.com/in/...",
          "accepted": true,
          "meetingCount": 11,
          "meetingOrdinal": "11th meeting",
          "previouslyMetWith": ["christian@usergems.com"]
        }
      ]
    }
  ]
}
```

**Output - HTML:**
- Responsive design (max-width 600px)
- Meeting cards with borders
- Attendee avatars (40px circular)
- Company info badges
- HTML-escaped content (XSS prevention)

#### EmailStorageService.java
**Purpose:** Persist generated emails with idempotency.

**Idempotency:**
- Unique constraint on `(user_id, email_date)`
- If email exists for date → Update it
- Otherwise → Create new

---

### 4. Scheduler (`scheduler/`)

#### MorningEmailScheduler.java
**Purpose:** Trigger email generation at 8am in each user's timezone.

**How It Works:**
```
@Scheduled(cron = "0 * * * * *")  // Every minute
public void checkAndSendMorningEmails() {
    for (User user : users) {
        ZonedDateTime userTime = ZonedDateTime.now(user.getTimezone());
        if (userTime.getHour() == 8 && userTime.getMinute() == 0) {
            generateAndStoreEmail(user);
        }
    }
}
```

**Why Check Every Minute?**
- Handles different timezones automatically
- DST transitions work correctly
- Each user gets email at their local 8am

**Configuration:**
```yaml
scheduler:
  enabled: ${SCHEDULER_ENABLED:true}
  morning-email-hour: 8
```

---

### 5. Controller (`controller/`)

#### ManualTriggerController.java
REST endpoints for testing and manual operations.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/trigger/user/{email}` | GET | Sync + generate email for user |
| `/api/trigger/user/{email}?date=2022-06-08` | GET | Generate for specific date |
| `/api/trigger/all` | GET | Process all users |
| `/api/trigger/email/{email}` | GET | Get JSON email content |
| `/api/trigger/email/{email}/html` | GET | Get HTML email content |
| `/api/trigger/sync/{email}` | GET | Sync only (no email) |
| `/api/trigger/users` | GET | List all users |

---

### 6. Configuration

#### application.yml (Base)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/morningprep
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
  flyway:
    enabled: true
    locations: classpath:db/migration

usergems:
  calendar-api:
    base-url: https://app.usergems.com/api/hiring/calendar-challenge
    timeout-seconds: 30
  person-api:
    base-url: https://app.usergems.com/api/hiring/calendar-challenge
    bearer-token: ${PERSON_API_TOKEN}
    cache-ttl-days: 30
```

#### application-local.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/morningprep
  jpa:
    hibernate:
      ddl-auto: none  # Flyway handles schema
    show-sql: true
  flyway:
    enabled: true

scheduler:
  enabled: false  # Manual triggering only

logging:
  level:
    com.usergems: DEBUG
```

---

### 7. Database Schema

```sql
-- Users (sales reps)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    calendar_api_key VARCHAR(255) NOT NULL,
    timezone VARCHAR(50) DEFAULT 'UTC',
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Calendar events (synced from API)
CREATE TABLE calendar_events (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(500),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_emails TEXT[],
    rejected_emails TEXT[],
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(external_id, user_id)
);

-- Persons (cached from enrichment API)
CREATE TABLE persons (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    avatar_url TEXT,
    title VARCHAR(500),
    linkedin_url TEXT,
    company_name VARCHAR(255),
    company_linkedin_url TEXT,
    company_employees INTEGER,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    not_found BOOLEAN DEFAULT FALSE
);

-- Generated emails
CREATE TABLE emails (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    email_date DATE NOT NULL,
    subject VARCHAR(500),
    content_json JSONB NOT NULL,
    content_html TEXT,
    meeting_count INTEGER,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(user_id, email_date)
);
```

---

## Data Flow

### Email Generation Flow

```
1. ManualTriggerController receives request
   └─▶ /api/trigger/user/stephan@usergems.com?date=2022-06-08

2. CalendarSyncService.syncEventsForUser()
   ├─▶ CalendarApiClient fetches new/changed events
   ├─▶ Upserts events to calendar_events table
   └─▶ Updates user.lastSyncAt

3. EmailGenerationService.generateEmail()
   └─▶ MeetingAggregationService.aggregateMeetingsForDate()
       ├─▶ Query calendar_events for date range
       ├─▶ Separate internal vs external attendees
       ├─▶ PersonEnrichmentService.getPersonsByEmails()
       │   ├─▶ Check persons table cache
       │   ├─▶ PersonApiClient for cache misses
       │   └─▶ Cache new person data
       ├─▶ Calculate meeting counts & previouslyMetWith
       └─▶ Build MeetingDto objects

4. EmailGenerationService
   ├─▶ Serialize to JSON
   └─▶ Generate HTML with inline CSS

5. EmailStorageService.storeEmail()
   └─▶ Upsert to emails table

6. Return success response with meeting count
```

---

## Running the Application

### Local Development

```bash
# 1. Start PostgreSQL (Docker or local)
docker run -d --name morningprep-db \
  -e POSTGRES_DB=morningprep \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:17

# 2. Run the app
export JAVA_HOME=/path/to/java21
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. Test endpoints
curl http://localhost:8080/api/trigger/users
curl "http://localhost:8080/api/trigger/user/stephan@usergems.com?date=2022-06-08"
curl http://localhost:8080/api/trigger/email/stephan@usergems.com/html > email.html
open email.html
```

### Docker

```bash
docker-compose up --build
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **RestClient over WebClient** | Synchronous calls are simpler; no reactive complexity needed at this scale |
| **PostgreSQL TEXT[] for emails** | Efficient for small arrays; avoids join tables |
| **Database person cache** | No Redis needed; 30-day TTL with simple timestamp check |
| **Minute-based scheduler** | Handles timezones and DST correctly |
| **JSONB for email content** | Queryable, indexable, flexible schema evolution |
| **Incremental sync** | Minimizes API calls; stops at already-synced events |
| **Spring Boot 4 + Hibernate 7** | Latest LTS stack with modern features |

---

## Spring Boot 4 Specifics

### Modularized Autoconfiguration

Spring Boot 4 split `spring-boot-autoconfigure` into smaller modules. Key change:

```kotlin
// Before (Spring Boot 3)
implementation("org.flywaydb:flyway-core")

// After (Spring Boot 4) - Need explicit starter
implementation("org.springframework.boot:spring-boot-starter-flyway")
implementation("org.flywaydb:flyway-database-postgresql")
```

### Hibernate 7 Array Support

```kotlin
// Need Hibernate 7-compatible version
implementation("io.hypersistence:hypersistence-utils-hibernate-70:3.12.0")
```

---

## Test Users

| Email | Timezone | API Key |
|-------|----------|---------|
| stephan@usergems.com | America/Los_Angeles | 7S$16U^FmxkdV!1b |
| christian@usergems.com | America/New_York | Ay@T3ZwF3YN^fZ@M |
| joss@usergems.com | Europe/London | PK7UBPVeG%3pP9%B |
| blaise@usergems.com | Europe/Paris | c0R*4iQK21McwLww |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Flyway not running | Ensure `spring-boot-starter-flyway` is in dependencies (SB4 change) |
| Tables not created | Check `ddl-auto: none` with Flyway enabled |
| Connection refused on macOS | Use `127.0.0.1` instead of `localhost` for Docker |
| Read-only transaction error | Remove `readOnly=true` from services that delegate to write operations |
| Date parsing errors | API uses `yyyy-M-d H:mm:ss` (single-digit day/month) |
