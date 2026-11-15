alter table graduation_request add column registry_number varchar(20);
alter table graduation_request add column legacy_title_number varchar(50);
alter table import_file drop constraint import_file_entity_type_enum;
alter table import_file add constraint import_file_entity_type_enum check (entity_type in ('PERSONS', 'PROGRAMS', 'SUBJECTS', 'CURRICULUM', 'PREREQUISITES', 'TERMS', 'SECTIONS', 'PROGRAM_ENROLLMENTS', 'SECTION_ENROLLMENTS', 'GRADE_ITEMS', 'ATTENDANCES', 'GRADUATIONS'));
alter table import_row drop constraint import_row_entity_type_enum;
alter table import_row add constraint import_row_entity_type_enum check (entity_type in ('PERSONS', 'PROGRAMS', 'SUBJECTS', 'CURRICULUM', 'PREREQUISITES', 'TERMS', 'SECTIONS', 'PROGRAM_ENROLLMENTS', 'SECTION_ENROLLMENTS', 'GRADE_ITEMS', 'ATTENDANCES', 'GRADUATIONS'));
alter table import_created_entity drop constraint import_created_entity_entity_type_enum;
alter table import_created_entity add constraint import_created_entity_entity_type_enum check (entity_type in ('PERSONS', 'PROGRAMS', 'SUBJECTS', 'CURRICULUM', 'PREREQUISITES', 'TERMS', 'SECTIONS', 'PROGRAM_ENROLLMENTS', 'SECTION_ENROLLMENTS', 'GRADE_ITEMS', 'ATTENDANCES', 'GRADUATIONS'));
