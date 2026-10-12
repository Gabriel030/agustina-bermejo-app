do $$
begin
    if exists (select 1 from late_fee_charge) then
        raise exception 'V2-09b2: late_fee_charge tiene recargos mensuales; regenerar la base demo';
    end if;
end $$;

update configuration_version
set config_values = (config_values - 'monthlyLateFeeRate') || jsonb_build_object(
        'dailyLateFeeRate', jsonb_build_object('fraction', round((config_values -> 'monthlyLateFeeRate' ->> 'fraction')::numeric / 30, 4)),
        'lateFeeChargingEnabled', false)
where scope = 'LATE_PAYMENT';

insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select v.id, last.to_status, last.to_status, last.event, 'DailyLateFeeMigration', now(),
       'D-330: la tasa de mora pasa a ser diaria; el cobro queda deshabilitado hasta que el director revise la tasa (D-210)'
from configuration_version v
join lateral (
    select c.to_status, c.event from configuration_version_status_change c
    where c.configuration_version_id = v.id order by c.id desc limit 1
) last on true
where v.scope = 'LATE_PAYMENT' and last.to_status = v.status;

create extension if not exists btree_gist;

alter table late_fee_charge drop constraint uk_late_fee_charge_origin_month;
alter table late_fee_charge drop column month_ordinal;
alter table late_fee_charge rename column monthly_rate to daily_rate;
alter table late_fee_charge
    add column period_start date not null,
    add column period_end date not null,
    add column charged_days integer not null,
    add column cutoff_kind varchar(20) not null,
    add column settlement_allocation_id bigint references payment_allocation (id);

alter table late_fee_charge
    add constraint ck_late_fee_charge_cutoff_kind check (cutoff_kind in ('MONTH_CLOSE', 'SETTLEMENT')),
    add constraint ck_late_fee_charge_period_order check (period_end >= period_start),
    add constraint ck_late_fee_charge_period_one_month check (date_trunc('month', period_start) = date_trunc('month', period_end)),
    add constraint ck_late_fee_charge_fact_date_is_period_end check (fact_date = period_end),
    add constraint ck_late_fee_charge_charged_days check (charged_days between 1 and (period_end - period_start + 1)),
    add constraint ck_late_fee_charge_settlement_allocation check ((cutoff_kind = 'SETTLEMENT') = (settlement_allocation_id is not null)),
    add constraint uk_late_fee_charge_origin_period_start unique (origin_invoice_id, period_start),
    add constraint ex_late_fee_charge_no_overlapping_days exclude using gist (origin_invoice_id with =, daterange(period_start, period_end, '[]') with &&);
