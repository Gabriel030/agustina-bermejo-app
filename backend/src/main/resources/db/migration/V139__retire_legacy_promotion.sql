do $$
declare
    pending_redemptions bigint;
begin
    select count(*) into pending_redemptions
    from promotion_redemption r
             join payment_plan_selection s on s.id = r.payment_plan_selection_id
             join promotion_benefit b on b.promotion_id = r.promotion_id
    where s.effective_to is null
      and b.invoice_scope_count is not null
      and b.invoice_scope_count > (select count(*)
                                   from invoice_discount d
                                            join invoice_line l on l.id = d.invoice_line_id
                                            join invoice i on i.id = l.invoice_id
                                            join student_current_account a on a.id = i.student_current_account_id
                                   where d.discount_type = 'PROMOTION'
                                     and d.student_discount_id = b.id
                                     and i.document_status <> 'DISCARDED'
                                     and a.student_id = coalesce((select pe.student_id from program_enrollment pe where pe.id = s.program_enrollment_id),
                                                                 (select ce.student_id from course_enrollment ce where ce.id = s.course_enrollment_id)));
    if pending_redemptions > 0 then
        raise exception 'Cannot drop the legacy promotion tables: % redemption(s) with an open payment plan selection still have installments pending. Use or close them before migrating.', pending_redemptions;
    end if;
end
$$;

alter table invoice_discount drop constraint invoice_discount_discount_type_check;
alter table invoice_discount disable trigger trg_invoice_discount_immutable_when_issued;
update invoice_discount set discount_type = 'COHORT_PROMOTION', student_discount_id = null where discount_type = 'PROMOTION';
alter table invoice_discount enable trigger trg_invoice_discount_immutable_when_issued;
alter table invoice_discount add constraint invoice_discount_discount_type_check
    check (discount_type in ('SCHOLARSHIP', 'COHORT_PROMOTION', 'ANNUAL_UPFRONT', 'COHORT_ENTRY', 'INDIVIDUAL'));

drop table promotion_redemption, promotion_benefit, promotion;
