# Morning Meeting Prep Email System

A Spring Boot application that enriches calendar meetings for sales representatives with relevant attendee information and delivers a consolidated "Morning Update" email at 8am daily.

## Tech Stack

- **Java 21** (LTS)
- **Spring Boot 4.0.1**
- **PostgreSQL 17**
- **Gradle** (Kotlin DSL)
- **Docker & Docker Compose**

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Build and start all services
docker-compose up --build

# The API will be available at http://localhost:8080
```

### Local Development

1. Start PostgreSQL:
   ```bash
   docker run -d --name morningprep-db \
     -e POSTGRES_DB=morningprep \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 \
     postgres:17-alpine
   ```

2. Run the application:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/trigger/user/{email}` | Trigger morning email for a specific user |
| `GET /api/trigger/all` | Trigger morning email for all users |
| `GET /api/trigger/email/{email}?date=YYYY-MM-DD` | Get generated email JSON |
| `GET /actuator/health` | Health check |

## Example Usage

```bash
# Trigger email generation for a user
curl http://localhost:8080/api/trigger/user/stephan@usergems.com

# Get the generated email JSON
curl http://localhost:8080/api/trigger/email/stephan@usergems.com | jq

# Trigger for all users
curl http://localhost:8080/api/trigger/all
```

## Test Users

| Email | Timezone |
|-------|----------|
| stephan@usergems.com | America/Los_Angeles |
| christian@usergems.com | America/New_York |
| joss@usergems.com | Europe/London |
| blaise@usergems.com | Europe/Paris |

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Calendar API   │────▶│   Application    │────▶│  Emails Table   │
│  (External)     │     │   (Core Logic)   │     │  (Database)     │
└─────────────────┘     └────────┬─────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │  Person Data API │
                        │  (External)      │
                        └──────────────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │  Person Cache    │
                        │  (30-day TTL)    │
                        └──────────────────┘
```

