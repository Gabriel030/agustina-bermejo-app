alter table program_enrollment
    add constraint ck_program_enrollment_withdrawn_has_effective_date
        check (status <> 'WITHDRAWN' or withdrawal_effective_date is not null) not valid;

alter table program_enrollment
    add constraint ck_program_enrollment_completed_has_completion_date
        check (status <> 'COMPLETED' or completion_date is not null) not valid;
