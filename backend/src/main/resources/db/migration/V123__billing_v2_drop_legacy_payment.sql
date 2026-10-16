do $$
declare
    dangling bigint;
    orphan_entries bigint;
begin
    select count(*) into orphan_entries
    from current_account_entry e
    where e.payment_id is not null
      and not exists (select 1 from payment p where p.id = e.payment_id and p.student_current_account_id = e.student_current_account_id);
    if orphan_entries > 0 then
        raise exception 'V123 aborted: % current account entr(ies) point to a payment that is not in the payment table for the same account (legacy payments); regenerate the database instead of dropping legacy_payment', orphan_entries;
    end if;
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

alter table current_account_entry add constraint fk_current_account_entry_payment
    foreign key (payment_id, student_current_account_id) references payment (id, student_current_account_id);
