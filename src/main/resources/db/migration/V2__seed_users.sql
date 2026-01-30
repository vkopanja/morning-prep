-- Seed test users for Morning Prep Email System
-- These are the 4 test users from the specification

INSERT INTO users (email, calendar_api_key, timezone, created_at) VALUES
    ('stephan@usergems.com', '7S$16U^FmxkdV!1b', 'America/Los_Angeles', NOW()),
    ('christian@usergems.com', 'Ay@T3ZwF3YN^fZ@M', 'America/New_York', NOW()),
    ('joss@usergems.com', 'PK7UBPVeG%3pP9%B', 'Europe/London', NOW()),
    ('blaise@usergems.com', 'c0R*4iQK21McwLww', 'Europe/Paris', NOW())
ON CONFLICT (email) DO UPDATE SET
    calendar_api_key = EXCLUDED.calendar_api_key,
    timezone = EXCLUDED.timezone;
