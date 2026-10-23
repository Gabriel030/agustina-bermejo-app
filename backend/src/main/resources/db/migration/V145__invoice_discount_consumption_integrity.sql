do $$
declare
    without_student_discount bigint;
    dangling_student_discount bigint;
begin
    select count(*) into without_student_discount from invoice_discount where student_discount_id is null;
    if without_student_discount > 0 then
        raise exception 'invoice_discount has % row(s) without student_discount_id; link or remove them before enforcing D-199', without_student_discount;
    end if;
    select count(*) into dangling_student_discount from invoice_discount d where not exists (select 1 from student_discount s where s.id = d.student_discount_id);
    if dangling_student_discount > 0 then
        raise exception 'invoice_discount has % row(s) pointing to a missing student_discount; fix them before enforcing D-199', dangling_student_discount;
    end if;
end;
$$;

update invoice_discount d
set consumption_released_at = l.charge_key_released_at
from invoice_line l
where l.id = d.invoice_line_id
  and l.charge_key_released_at is not null
  and d.consumption_released_at is null;

alter table invoice_discount alter column student_discount_id set not null;
alter table invoice_discount add constraint fk_invoice_discount_student_discount foreign key (student_discount_id) references student_discount (id);
create index ix_invoice_discount_student_discount on invoice_discount (student_discount_id);

create unique index uk_invoice_discount_consumption on invoice_discount (student_discount_id, installment_number)
    where consumption_released_at is null and installment_number is not null;
