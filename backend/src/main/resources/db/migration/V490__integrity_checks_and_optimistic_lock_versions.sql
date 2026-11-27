alter table subject_section
    add constraint subject_section_capacity_positive check (capacity is null or capacity > 0) not valid;

alter table course_section
    add constraint course_section_capacity_positive check (capacity is null or capacity > 0) not valid;

alter table course
    add constraint course_capacity_positive check (capacity is null or capacity > 0) not valid;

alter table classroom
    add constraint classroom_capacity_positive check (capacity > 0) not valid;

alter table subject_section_enrollment
    add constraint subject_section_enrollment_attempt_number_positive check (attempt_number is null or attempt_number >= 1) not valid;

alter table course_section_enrollment
    add constraint course_section_enrollment_attempt_number_positive check (attempt_number is null or attempt_number >= 1) not valid;

alter table term add column version bigint not null default 0;
alter table program add column version bigint not null default 0;
alter table evaluation_scheme add column version bigint not null default 0;

create index if not exists ix_processed_effect_processed_at on processed_effect (processed_at);
