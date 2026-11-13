alter table person
    add column token_version integer not null default 0,
    add column reset_failed_attempts integer not null default 0,
    add column reset_locked_until timestamp;
