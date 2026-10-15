alter table program_enrollment
    add column withdrawal_effective_date date,
    add column withdrawn_by_person_id bigint references person (id);

alter table course_enrollment
    add column withdrawal_effective_date date,
    add column withdrawn_by_person_id bigint references person (id);

update program_enrollment
set withdrawal_effective_date = completion_date::date
where status = 'WITHDRAWN' and completion_date is not null;

update course_enrollment
set withdrawal_effective_date = completion_date::date
where status = 'WITHDRAWN' and completion_date is not null;

alter table program_enrollment
    add constraint ck_program_enrollment_withdrawal_date_only_when_withdrawn
        check (withdrawal_effective_date is null or status = 'WITHDRAWN');

alter table course_enrollment
    add constraint ck_course_enrollment_withdrawal_date_only_when_withdrawn
        check (withdrawal_effective_date is null or status = 'WITHDRAWN');
