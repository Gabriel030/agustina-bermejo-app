ALTER TABLE person ADD COLUMN email_verified BOOLEAN;

CREATE TABLE email_verification_token (
    id        BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL
);
