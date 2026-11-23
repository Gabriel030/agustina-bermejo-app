alter table regular_student_certificate drop constraint regular_student_certificate_standing_basis_enum;
alter table regular_student_certificate
    add constraint regular_student_certificate_standing_basis_enum
    check (standing_basis in ('BILLING_NOT_ENFORCED', 'BILLING_VERIFIED', 'EXCEPTION_APPROVED', 'MANUALLY_CONFIRMED'));
