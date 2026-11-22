do $$
declare
    orphan_count bigint;
    orphan_ids text;
begin
    select count(*), string_agg(r.id::text, ', ' order by r.id) into orphan_count, orphan_ids
    from annual_upfront_request r
    where r.payment_id is not null
      and not exists (select 1 from payment p where p.id = r.payment_id);
    if orphan_count > 0 then
        raise exception 'annual_upfront_request has % row(s) whose payment_id does not exist in payment (ids: %); fix them before adding fk_annual_upfront_request_payment', orphan_count, orphan_ids;
    end if;
end $$;

alter table annual_upfront_request
    add constraint fk_annual_upfront_request_payment foreign key (payment_id) references payment (id) not valid;
