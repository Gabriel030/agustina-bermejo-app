alter table student_discount
    add constraint ck_student_discount_exhausted_has_installments check (status <> 'EXHAUSTED' or installments_total is not null);
