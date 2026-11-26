alter table program
    add column if not exists catalog_area varchar(40),
    add column if not exists catalog_published_at timestamp;

alter table course
    add column if not exists catalog_area varchar(40),
    add column if not exists catalog_published_at timestamp;

alter table program drop constraint if exists program_catalog_area_enum;
alter table program
    add constraint program_catalog_area_enum check (catalog_area in ('BUSINESS_ADMINISTRATION', 'DESIGN', 'HEALTH', 'LANGUAGES_AND_COMMUNICATION', 'SOCIAL_SCIENCES', 'TECHNOLOGY'));

alter table course drop constraint if exists course_catalog_area_enum;
alter table course
    add constraint course_catalog_area_enum check (catalog_area in ('BUSINESS_ADMINISTRATION', 'DESIGN', 'HEALTH', 'LANGUAGES_AND_COMMUNICATION', 'SOCIAL_SCIENCES', 'TECHNOLOGY'));

alter table program drop constraint if exists program_level_enum;
alter table program
    add constraint program_level_enum check (level in ('BACHELOR', 'DIPLOMA', 'EXTENSION', 'GRADUATE', 'UNDERGRADUATE')) not valid;
alter table program validate constraint program_level_enum;
