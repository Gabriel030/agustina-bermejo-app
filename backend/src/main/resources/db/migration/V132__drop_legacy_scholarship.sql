do $$
begin
    if exists (select 1 from scholarship where status = 'ACTIVE') then
        raise exception 'Cannot drop the legacy scholarship tables while ACTIVE legacy scholarships exist; revoke or re-grant them as student discounts first';
    end if;
end
$$;

alter table invoice_discount disable trigger trg_invoice_discount_immutable_when_issued;
update invoice_discount set student_discount_id = null where source_installment_id is not null;
alter table invoice_discount enable trigger trg_invoice_discount_immutable_when_issued;

alter table invoice_discount drop column source_installment_id;

drop table scholarship_installment;

drop table scholarship;
