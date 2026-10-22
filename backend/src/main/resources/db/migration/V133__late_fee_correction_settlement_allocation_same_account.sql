alter table late_fee_charge_correction drop constraint fk_late_fee_charge_correction_settlement_allocation;

alter table late_fee_charge_correction
    add constraint fk_late_fee_charge_correction_settlement_allocation foreign key (settlement_allocation_id, student_current_account_id)
        references payment_allocation (id, student_current_account_id);
