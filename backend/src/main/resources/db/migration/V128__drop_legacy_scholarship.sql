do $$
begin
    if exists (select 1 from scholarship where status = 'ACTIVE') then
        raise exception 'Cannot drop the legacy scholarship tables while ACTIVE legacy scholarships exist; revoke or re-grant them as student discounts first';
    end if;
end
$$;

alter table invoice_discount drop column source_installment_id;

drop table scholarship_installment;

drop table scholarship;
