do $$
declare
    actor_id bigint := (select min(id) from person);
begin
    if actor_id is null then
        return;
    end if;

    insert into price (program_id, fee_type, currency, description)
    select p.id, 'MONTHLY_INSTALLMENT', p.currency, 'Cuota mensual'
    from program p
    where p.price > 0 and p.price < 10000000000
      and not exists (select 1 from price x where x.program_id = p.id and x.fee_type = 'MONTHLY_INSTALLMENT');

    insert into price (program_id, fee_type, currency, description)
    select p.id, 'ENROLLMENT_FEE', p.currency, 'Matrícula'
    from program p
    where p.enrollment_fee_amount > 0 and p.enrollment_fee_amount < 10000000000
      and not exists (select 1 from price x where x.program_id = p.id and x.fee_type = 'ENROLLMENT_FEE');

    insert into price (course_id, fee_type, currency, description)
    select c.id, 'MONTHLY_INSTALLMENT', c.currency, 'Cuota mensual'
    from course c
    where c.price > 0 and c.price < 10000000000
      and not exists (select 1 from price x where x.course_id = c.id and x.fee_type = 'MONTHLY_INSTALLMENT');

    with inserted as (
        insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication, initial_version)
        select pr.id, source.amount, pr.currency, date '2000-01-01', 'EFFECTIVE', 'OfferingPriceBackfill: precio inicial migrado desde la oferta',
               actor_id, now(), actor_id, now(), true, true
        from price pr
        join (
            select p.id as program_id, null::bigint as course_id, 'MONTHLY_INSTALLMENT' as fee_type, p.price as amount
            from program p where p.price > 0 and p.price < 10000000000
            union all
            select p.id, null, 'ENROLLMENT_FEE', p.enrollment_fee_amount
            from program p where p.enrollment_fee_amount > 0 and p.enrollment_fee_amount < 10000000000
            union all
            select null, c.id, 'MONTHLY_INSTALLMENT', c.price
            from course c where c.price > 0 and c.price < 10000000000
        ) source on source.fee_type = pr.fee_type
                and source.program_id is not distinct from pr.program_id
                and source.course_id is not distinct from pr.course_id
        where not exists (select 1 from price_version v where v.price_id = pr.id)
        returning id, change_reason, proposed_at
    )
    insert into price_version_status_change (price_version_id, from_status, to_status, event, actor_process, changed_at, reason)
    select id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'OfferingPriceBackfill', proposed_at, change_reason
    from inserted;
end
$$;

comment on column program.price is 'DEPRECADA (DB-9): copia sin escritores; la fuente es price_version. Se elimina en el paquete siguiente.';
comment on column program.enrollment_fee_amount is 'DEPRECADA (DB-9): copia sin escritores; la fuente es price_version. Se elimina en el paquete siguiente.';
comment on column course.price is 'DEPRECADA (DB-9): copia sin escritores; la fuente es price_version. Se elimina en el paquete siguiente.';
