do $$
declare
    dangling bigint;
begin
    select count(*) into dangling
    from payment_allocation_source s
    where s.payment_id is not null
      and not exists (select 1 from payment p where p.id = s.payment_id);
    if dangling > 0 then
        raise exception 'V123 aborted: % allocation source(s) point to a payment id that is not in the payment table (legacy payments); regenerate the database instead of dropping legacy_payment', dangling;
    end if;
end
$$;

drop function backfill_payment_allocation_sources();

drop table legacy_payment_allocation;

drop table legacy_payment;
