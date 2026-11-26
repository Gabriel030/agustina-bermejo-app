alter table course_enrollment
    add constraint ck_course_enrollment_registered_withdrawal_has_effective_date
        check (status <> 'WITHDRAWN' or withdrawal_registered_on is null or withdrawal_effective_date is not null) not valid;
