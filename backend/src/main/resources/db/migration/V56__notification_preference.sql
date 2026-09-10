CREATE TABLE notification_preference (
    id         BIGSERIAL PRIMARY KEY,
    person_id  BIGINT NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    event      VARCHAR(64) NOT NULL,
    channel    VARCHAR(32) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notification_preference UNIQUE (person_id, event, channel)
);
