create table document_issuance_settings (
    id smallint primary key default 1,
    version bigint not null default 0,
    exam_registration_advance_days integer not null default 15,
    exam_attendance_validity_days integer not null default 30,
    approved_subjects_validity_days integer not null default 30,
    daily_issuance_limit_per_type integer not null default 1,
    updated_at timestamp,
    updated_by_id bigint,
    constraint ck_document_issuance_settings_singleton check (id = 1),
    constraint ck_document_issuance_settings_advance check (exam_registration_advance_days between 0 and 365),
    constraint ck_document_issuance_settings_attendance check (exam_attendance_validity_days between 1 and 365),
    constraint ck_document_issuance_settings_approved check (approved_subjects_validity_days between 1 and 365),
    constraint ck_document_issuance_settings_daily_limit check (daily_issuance_limit_per_type between 1 and 50)
);

insert into document_issuance_settings (id) values (1);
