alter table password_reset_token drop constraint uk_password_reset_token_hash;
alter table password_reset_token add column attempts integer not null default 0;
