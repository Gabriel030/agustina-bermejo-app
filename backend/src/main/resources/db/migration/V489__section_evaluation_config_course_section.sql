alter table section_evaluation_config
    add column course_section_id bigint;

alter table section_evaluation_config
    alter column subject_section_id drop not null;

alter table section_evaluation_config
    add constraint fk_section_eval_config_course_section foreign key (course_section_id) references course_section,
    add constraint uk_section_eval_config_course_section unique (course_section_id),
    add constraint ck_section_eval_config_single_owner check (num_nonnulls(subject_section_id, course_section_id) = 1);
