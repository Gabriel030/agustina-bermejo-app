alter table late_fee_charge_correction alter column cause type varchar(40);
alter table late_fee_charge_correction alter column enrollment_end_date drop not null;
alter table late_fee_charge_correction add column settlement_allocation_id bigint;
alter table late_fee_charge_correction add column payment_date date;

alter table late_fee_charge_correction
    add constraint fk_late_fee_charge_correction_settlement_allocation foreign key (settlement_allocation_id) references payment_allocation (id);

alter table late_fee_charge_correction drop constraint ck_late_fee_charge_correction_cause;
alter table late_fee_charge_correction add constraint ck_late_fee_charge_correction_cause
    check (cause in ('BACKDATED_WITHDRAWAL', 'PAYMENT_DATED_BEFORE_CHARGED_DAYS', 'PAYMENT_WITHIN_GRACE'));

alter table late_fee_charge_correction drop constraint ck_late_fee_charge_correction_after_end;
alter table late_fee_charge_correction add constraint ck_late_fee_charge_correction_after_end
    check (cause <> 'BACKDATED_WITHDRAWAL' or (enrollment_end_date is not null and first_corrected_day > enrollment_end_date));

alter table late_fee_charge_correction add constraint ck_late_fee_charge_correction_reference_by_cause
    check ((cause = 'BACKDATED_WITHDRAWAL') = (settlement_allocation_id is null and payment_date is null)
           and (cause <> 'BACKDATED_WITHDRAWAL' or enrollment_end_date is not null)
           and (cause = 'BACKDATED_WITHDRAWAL' or (settlement_allocation_id is not null and payment_date is not null and enrollment_end_date is null)));

alter table late_fee_charge_correction add constraint ck_late_fee_charge_correction_after_payment
    check (cause <> 'PAYMENT_DATED_BEFORE_CHARGED_DAYS' or first_corrected_day > payment_date);

create index ix_late_fee_charge_correction_settlement_allocation on late_fee_charge_correction (settlement_allocation_id);
