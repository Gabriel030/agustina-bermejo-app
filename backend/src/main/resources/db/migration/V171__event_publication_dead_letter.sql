CREATE TABLE event_publication_attempt
(
  publication_id  UUID PRIMARY KEY,
  attempts        INTEGER NOT NULL,
  last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE event_publication_dead_letter
(
  publication_id   UUID PRIMARY KEY,
  listener_id      TEXT NOT NULL,
  event_type       TEXT NOT NULL,
  serialized_event TEXT NOT NULL,
  publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
  attempts         INTEGER NOT NULL,
  dead_lettered_at TIMESTAMP WITH TIME ZONE NOT NULL
);
