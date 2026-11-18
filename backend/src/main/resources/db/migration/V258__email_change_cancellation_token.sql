ALTER TABLE email_verification_token ADD COLUMN cancel_token_hash VARCHAR(255) UNIQUE;
