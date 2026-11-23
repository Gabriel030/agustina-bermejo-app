alter table certificate_exception_approval add column debt_total_at_request numeric(12, 2);
alter table certificate_exception_approval add column debt_currency_at_request varchar(3);
alter table certificate_exception_approval add column overdue_installments_at_request integer;

alter table certificate_exception_approval add constraint ck_certificate_exception_debt_at_request
    check (((debt_total_at_request is null) = (debt_currency_at_request is null))
        and ((debt_total_at_request is null) = (overdue_installments_at_request is null))
        and (debt_total_at_request is null or (debt_total_at_request >= 0 and overdue_installments_at_request >= 0)));
alter table certificate_exception_approval add constraint ck_certificate_exception_debt_currency
    check (debt_currency_at_request in ('ARS', 'USD'));
