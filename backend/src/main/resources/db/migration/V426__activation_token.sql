create table activation_token (
    id bigserial primary key,
    person_id bigint not null references person(id) on delete cascade,
    email varchar(255) not null,
    token_hash varchar(64) not null,
    expires_at timestamp not null,
    used_at timestamp,
    created_at timestamp not null,
    constraint uq_activation_token_token_hash unique (token_hash)
);

create index idx_activation_token_person_id on activation_token (person_id);
