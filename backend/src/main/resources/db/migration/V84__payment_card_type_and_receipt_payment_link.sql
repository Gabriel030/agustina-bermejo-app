alter table payment add column card_type varchar(10);
alter table payment add constraint ck_payment_card_type check (card_type is null or card_type in ('CREDIT', 'DEBIT'));

alter table payment_receipt add column payment_id bigint;
alter table payment_receipt add constraint fk_payment_receipt_payment foreign key (payment_id) references payment (id);
create index ix_payment_receipt_payment_id on payment_receipt (payment_id);
create index ix_payment_receipt_invoice_id on payment_receipt (invoice_id);
