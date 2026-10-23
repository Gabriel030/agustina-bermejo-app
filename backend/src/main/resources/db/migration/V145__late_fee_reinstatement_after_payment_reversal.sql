alter table late_fee_charge add column reinstates_correction_id bigint;

alter table late_fee_charge
    add constraint fk_late_fee_charge_reinstated_correction foreign key (reinstates_correction_id) references late_fee_charge_correction (id),
    add constraint uk_late_fee_charge_reinstated_correction unique (reinstates_correction_id);

alter table late_fee_charge drop constraint ck_late_fee_charge_cutoff_kind;
alter table late_fee_charge add constraint ck_late_fee_charge_cutoff_kind check (cutoff_kind in ('MONTH_CLOSE', 'SETTLEMENT', 'REINSTATEMENT'));
alter table late_fee_charge add constraint ck_late_fee_charge_reinstatement check ((cutoff_kind = 'REINSTATEMENT') = (reinstates_correction_id is not null));

alter table late_fee_charge drop constraint uk_late_fee_charge_origin_period_start;
create unique index uk_late_fee_charge_origin_period_start on late_fee_charge (origin_invoice_id, period_start) where reinstates_correction_id is null;

alter table late_fee_charge drop constraint ex_late_fee_charge_no_overlapping_days;
alter table late_fee_charge add constraint ex_late_fee_charge_no_overlapping_days
    exclude using gist (origin_invoice_id with =, daterange(period_start, period_end, '[]') with &&) where (reinstates_correction_id is null);

create function check_late_fee_reinstatement_matches_its_correction() returns trigger language plpgsql as $$
declare
    corrected record;
begin
    if new.reinstates_correction_id is null then
        return new;
    end if;
    select k.first_corrected_day, k.last_corrected_day, k.corrected_days, c.origin_invoice_id into corrected
    from late_fee_charge_correction k join late_fee_charge c on c.id = k.late_fee_charge_id
    where k.id = new.reinstates_correction_id;
    if corrected.origin_invoice_id is distinct from new.origin_invoice_id
        or new.period_start < corrected.first_corrected_day or new.period_end > corrected.last_corrected_day
        or new.charged_days > corrected.corrected_days then
        raise exception 'A late fee reinstatement must stay inside the days its correction annulled' using errcode = '23514';
    end if;
    return new;
end $$;

create trigger trg_late_fee_charge_reinstatement_matches_correction before insert on late_fee_charge
    for each row execute function check_late_fee_reinstatement_matches_its_correction();
