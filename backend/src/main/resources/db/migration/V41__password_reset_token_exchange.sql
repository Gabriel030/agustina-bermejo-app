alter table password_reset_token add column exchange_token_hash varchar(255);
alter table password_reset_token add column exchange_expires_at timestamp;
