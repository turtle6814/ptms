CREATE TABLE event_referees (
    id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    referee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE (event_id, referee_id)
);
