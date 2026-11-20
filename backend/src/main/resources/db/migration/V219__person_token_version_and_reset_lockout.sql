alter table person
    add column token_version integer not null default 0,
    add column reset_lockout_notified_at timestamp;

create table password_reset_failure (
    person_id bigint not null references person (id) on delete cascade,
    source_hash varchar(64) not null,
    failures integer not null,
    window_start timestamp not null,
    primary key (person_id, source_hash)
);
