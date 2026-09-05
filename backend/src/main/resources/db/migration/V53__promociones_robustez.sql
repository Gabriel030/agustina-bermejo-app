alter table promotion
    add constraint chk_promotion_window check (eligibility_valid_from <= eligibility_valid_to);

alter table promotion_benefit
    add constraint uk_promotion_benefit_fee_type unique (promotion_id, fee_type);

create index ix_promotion_course on promotion (course_id);
create index ix_promotion_program on promotion (program_id);
create index ix_promotion_benefit_promotion on promotion_benefit (promotion_id);
create index ix_invoice_adjustment_promotion_benefit on invoice_adjustment (promotion_benefit_id);
create index ix_invoice_payment_plan_selection on invoice (payment_plan_selection_id);
