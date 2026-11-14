alter table subject_section_enrollment
    add column withdrawal_reason varchar(500),
    add column withdrawn_by_person_id bigint references person (id),
    add constraint ck_subject_section_enrollment_withdrawal_reason_length
        check (withdrawal_reason is null or char_length(withdrawal_reason) between 10 and 500);

alter table course_section_enrollment
    add column withdrawal_reason varchar(500),
    add column withdrawn_by_person_id bigint references person (id),
    add constraint ck_course_section_enrollment_withdrawal_reason_length
        check (withdrawal_reason is null or char_length(withdrawal_reason) between 10 and 500);

alter table program_enrollment
    add column withdrawal_reason varchar(500),
    add constraint ck_program_enrollment_withdrawal_reason_length
        check (withdrawal_reason is null or char_length(withdrawal_reason) between 10 and 500);

alter table course_enrollment
    add column withdrawal_reason varchar(500),
    add constraint ck_course_enrollment_withdrawal_reason_length
        check (withdrawal_reason is null or char_length(withdrawal_reason) between 10 and 500);

create index idx_subject_section_enrollment_withdrawn_by_person on subject_section_enrollment (withdrawn_by_person_id);
create index idx_course_section_enrollment_withdrawn_by_person on course_section_enrollment (withdrawn_by_person_id);
