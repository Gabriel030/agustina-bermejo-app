alter table regular_student_certificate add column template_version_id bigint references certificate_template_version (id);
alter table exam_registration_certificate add column template_version_id bigint references certificate_template_version (id);
alter table exam_certificate add column template_version_id bigint references certificate_template_version (id);
alter table approved_subjects_certificate add column template_version_id bigint references certificate_template_version (id);

create index ix_regular_student_certificate_template_version on regular_student_certificate (template_version_id) where template_version_id is not null;
create index ix_exam_registration_certificate_template_version on exam_registration_certificate (template_version_id) where template_version_id is not null;
create index ix_exam_certificate_template_version on exam_certificate (template_version_id) where template_version_id is not null;
create index ix_approved_subjects_certificate_template_version on approved_subjects_certificate (template_version_id) where template_version_id is not null;
