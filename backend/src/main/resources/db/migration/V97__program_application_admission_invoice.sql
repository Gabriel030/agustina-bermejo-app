alter table program_enrollment_application drop column if exists payment_receipt_id;
alter table program_enrollment_application add column admission_invoice_id bigint references invoice (id);
create unique index uk_program_enrollment_application_admission_invoice on program_enrollment_application (admission_invoice_id)
    where admission_invoice_id is not null;
