alter table invoice
    add constraint chk_invoice_status check (status in ('PENDING', 'OVERDUE', 'PAID', 'WAIVED', 'CANCELLED'));

alter table payment
    add constraint chk_payment_status check (status in ('PENDING_GATEWAY_CONFIRMATION', 'CONFIRMED', 'REJECTED'));

create index ix_course_enrollment_student on course_enrollment (student_id);
create index ix_course_enrollment_course on course_enrollment (course_id);
create index ix_program_enrollment_student on program_enrollment (student_id);
create index ix_grade_item_course_section_enrollment on grade_item (course_section_enrollment_id);
create index ix_grade_item_program_section_enrollment on grade_item (program_section_enrollment_id);
create index ix_payment_allocation_invoice on payment_allocation (invoice_id);
create index ix_password_reset_token_hash on password_reset_token (token_hash);
