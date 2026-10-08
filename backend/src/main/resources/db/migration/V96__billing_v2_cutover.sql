truncate table payment_allocation, credit_note_source_invoice, credit_application, bank_transfer_receipt, program_enrollment_application,
    course_enrollment_application cascade;
update scholarship_installment set invoice_id = null where invoice_id is not null;

drop table invoice_adjustment;
drop table legacy_invoice cascade;

alter table payment_allocation add constraint fk_payment_allocation_invoice foreign key (invoice_id) references invoice;
alter table credit_note_source_invoice add constraint fk_cnsi_invoice foreign key (invoice_id) references invoice;
alter table credit_application add constraint fk_ca_invoice foreign key (invoice_id) references invoice;
alter table bank_transfer_receipt add constraint fk_bank_transfer_receipt_invoice foreign key (invoice_id) references invoice;
alter table scholarship_installment add constraint fk_scholarship_installment_invoice foreign key (invoice_id) references invoice;

alter table invoice_line add column template_id bigint;
alter table invoice_line add column enrollment_fee_slot integer;
create index ix_invoice_line_template_slot on invoice_line (template_id, enrollment_fee_slot) where template_id is not null;

create or replace function forbid_issued_invoice_line_change() returns trigger language plpgsql as $$
begin
    if (select document_status from invoice where id = old.invoice_id) = 'ISSUED' then
        if tg_op = 'DELETE' or (to_jsonb(new) - 'credited_amount' - 'credited_amount_currency' - 'free_credited_amount'
                - 'free_credited_amount_currency' - 'charge_key_released_at')
                <> (to_jsonb(old) - 'credited_amount' - 'credited_amount_currency' - 'free_credited_amount'
                - 'free_credited_amount_currency' - 'charge_key_released_at') then
            if tg_op = 'UPDATE' and old.program_enrollment_id is null and old.course_enrollment_id is null
                    and (to_jsonb(new) - 'credited_amount' - 'credited_amount_currency' - 'free_credited_amount'
                        - 'free_credited_amount_currency' - 'charge_key_released_at' - 'program_enrollment_id' - 'course_enrollment_id')
                    = (to_jsonb(old) - 'credited_amount' - 'credited_amount_currency' - 'free_credited_amount'
                        - 'free_credited_amount_currency' - 'charge_key_released_at' - 'program_enrollment_id' - 'course_enrollment_id') then
                return new;
            end if;
            raise exception 'issued invoice line is immutable: % is not allowed', tg_op using errcode = 'integrity_constraint_violation';
        end if;
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;
