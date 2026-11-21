alter table graduation_certificate add column template_version_id bigint references certificate_template_version (id);
create index idx_graduation_certificate_template_version on graduation_certificate (template_version_id) where template_version_id is not null;

create table graduation_certificate_registry_counter (
    kind varchar(20) not null,
    registry_year integer not null,
    last_number integer not null,
    primary key (kind, registry_year),
    constraint graduation_certificate_registry_counter_kind_enum check (kind in ('COURSE', 'PROGRAM')),
    constraint ck_graduation_certificate_registry_counter_range check (registry_year between 2000 and 9999 and last_number between 1 and 9999999)
);

create unique index uq_graduation_certificate_registry_number on graduation_certificate (kind, registry_number) where registry_number ~ '^[0-9]{7}/[0-9]{4}$';
