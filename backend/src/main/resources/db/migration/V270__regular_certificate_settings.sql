create table regular_certificate_settings (
    id smallint primary key default 1,
    version bigint not null default 0,
    validity_days integer not null default 30,
    exception_validity_days integer not null default 7,
    evidence_required boolean not null default false,
    updated_at timestamp,
    updated_by_id bigint,
    constraint ck_regular_certificate_settings_singleton check (id = 1),
    constraint ck_regular_certificate_settings_validity check (validity_days between 1 and 180),
    constraint ck_regular_certificate_settings_exception_validity check (exception_validity_days between 1 and 180)
);

insert into regular_certificate_settings (id) values (1);
