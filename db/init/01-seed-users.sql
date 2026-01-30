-- Seed test users for Morning Prep Email System
-- This script runs automatically when the PostgreSQL container starts

-- Wait for Flyway migrations to complete by checking if the table exists
-- Note: This runs after Flyway since docker-compose seeds come after app init

-- Insert test users
INSERT INTO users (email, calendar_api_key, timezone, created_at) VALUES
    ('stephan@usergems.com', '7S$16U^FmxkdV!1b', 'America/Los_Angeles', NOW()),
    ('christian@usergems.com', 'Ay@T3ZwF3YN^fZ@M', 'America/New_York', NOW()),
    ('joss@usergems.com', 'PK7UBPVeG%3pP9%B', 'Europe/London', NOW()),
    ('blaise@usergems.com', 'c0R*4iQK21McwLww', 'Europe/Paris', NOW())
ON CONFLICT (email) DO UPDATE SET
    calendar_api_key = EXCLUDED.calendar_api_key,
    timezone = EXCLUDED.timezone;

-- Display confirmation
DO $$
BEGIN
    RAISE NOTICE 'Seeded % users', (SELECT COUNT(*) FROM users);
END $$;
