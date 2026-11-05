alter table legacy_payment alter column id drop identity;
alter table legacy_payment alter column id set default nextval('payment_id_seq');

select setval('payment_id_seq', greatest(
    (select last_value from payment_id_seq),
    coalesce((select max(id) from legacy_payment), 0),
    coalesce((select max(id) from payment), 0),
    1));
