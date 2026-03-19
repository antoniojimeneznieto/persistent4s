\connect eventlog

CREATE TABLE events (
    sequence_number BIGSERIAL PRIMARY KEY,
    event_type      TEXT    NOT NULL,
    tags            JSONB   NOT NULL DEFAULT '[]',
    payload         JSONB   NOT NULL DEFAULT '{}',
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_tags ON events USING GIN (tags);
CREATE INDEX idx_events_event_type ON events (event_type);

CREATE TABLE projection_cursors (
    projection_name      TEXT PRIMARY KEY,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);