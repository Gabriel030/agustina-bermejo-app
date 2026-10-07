create table authorization_request (
    id bigserial primary key,
    operation_type varchar(60) not null,
    operation_payload jsonb not null,
    required_role varchar(40) not null,
    reason varchar(500) not null,
    status varchar(20) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    requested_by_id bigint not null references person (id),
    requested_at timestamptz not null,
    decided_by_id bigint references person (id),
    decided_at timestamptz,
    decision_reason varchar(500),
    expires_on date,
    resume_outcome varchar(30) check (resume_outcome in ('PROCESSED', 'REJECTED_ON_RESUME')),
    version bigint not null default 0
);

create index authorization_request_status_expires_on_idx on authorization_request (status, expires_on);

create table authorization_request_status_change (
    id bigserial primary key,
    authorization_request_id bigint not null references authorization_request (id),
    from_status varchar(20),
    to_status varchar(20) not null,
    event varchar(20) not null,
    actor_person_id bigint references person (id),
    actor_process varchar(80),
    changed_at timestamptz not null,
    reason varchar(500),
    check ((actor_person_id is null) <> (actor_process is null))
);

create index authorization_request_status_change_request_idx on authorization_request_status_change (authorization_request_id);

create table processed_effect (
    event_id uuid not null,
    handler_name varchar(120) not null,
    processed_at timestamptz not null,
    primary key (event_id, handler_name)
);
