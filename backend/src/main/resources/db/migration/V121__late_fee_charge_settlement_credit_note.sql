alter table late_fee_charge
    add column settlement_credit_note_id bigint references credit_note (id);

alter table late_fee_charge
    drop constraint ck_late_fee_charge_settlement_allocation;

alter table late_fee_charge
    add constraint ck_late_fee_charge_settlement_source
        check ((cutoff_kind = 'SETTLEMENT') = (num_nonnulls(settlement_allocation_id, settlement_credit_note_id) = 1));

create index ix_late_fee_charge_settlement_credit_note on late_fee_charge (settlement_credit_note_id) where settlement_credit_note_id is not null;
