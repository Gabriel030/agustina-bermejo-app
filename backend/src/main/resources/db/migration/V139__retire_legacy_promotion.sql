do $$
declare
    pending_redemptions bigint;
begin
    select count(*) into pending_redemptions
    from promotion_redemption r
             join promotion_benefit b on b.promotion_id = r.promotion_id
    where b.invoice_scope_count is not null
      and b.invoice_scope_count > (select count(*)
                                   from invoice_discount d
                                            join invoice_line l on l.id = d.invoice_line_id
                                            join invoice i on i.id = l.invoice_id
                                   where d.discount_type = 'PROMOTION'
                                     and d.student_discount_id = b.id
                                     and i.document_status <> 'DISCARDED');
    if pending_redemptions > 0 then
        raise exception 'Cannot drop the legacy promotion tables: % redemption(s) still have installments pending. Use or close them before migrating.', pending_redemptions;
    end if;
end
$$;

alter table invoice_discount drop constraint invoice_discount_discount_type_check;
alter table invoice_discount disable trigger trg_invoice_discount_immutable_when_issued;
update invoice_discount set discount_type = 'COHORT_PROMOTION' where discount_type = 'PROMOTION';
alter table invoice_discount enable trigger trg_invoice_discount_immutable_when_issued;
alter table invoice_discount add constraint invoice_discount_discount_type_check
    check (discount_type in ('SCHOLARSHIP', 'COHORT_PROMOTION', 'ANNUAL_UPFRONT', 'COHORT_ENTRY', 'INDIVIDUAL'));

drop table promotion_redemption, promotion_benefit, promotion;
