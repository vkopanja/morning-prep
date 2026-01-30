-- Morning Prep Email System - Initial Schema
-- PostgreSQL 17

-- Users table (sales reps with calendar API keys)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    calendar_api_key TEXT NOT NULL,
    timezone TEXT DEFAULT 'UTC',
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_timezone ON users(timezone);

-- Calendar events table (synced from external API)
CREATE TABLE calendar_events (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_emails TEXT[] DEFAULT '{}',
    rejected_emails TEXT[] DEFAULT '{}',
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(external_id, user_id)
);

CREATE INDEX idx_events_user_start ON calendar_events(user_id, start_time);
CREATE INDEX idx_events_changed_at ON calendar_events(changed_at DESC);
CREATE INDEX idx_events_external_user ON calendar_events(external_id, user_id);

-- Persons cache table (30-day TTL for enrichment data)
CREATE TABLE persons (
    id BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    first_name TEXT,
    last_name TEXT,
    avatar_url TEXT,
    title TEXT,
    linkedin_url TEXT,
    company_name TEXT,
    company_linkedin_url TEXT,
    company_employees INTEGER,
    fetched_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    not_found BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_persons_email ON persons(email);
CREATE INDEX idx_persons_fetched_at ON persons(fetched_at);

-- Emails table (generated morning update emails)
CREATE TABLE emails (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_date DATE NOT NULL,
    subject TEXT,
    content_json JSONB NOT NULL,
    content_html TEXT,
    meeting_count INTEGER,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    UNIQUE(user_id, email_date)
);

CREATE INDEX idx_emails_user_date ON emails(user_id, email_date);
