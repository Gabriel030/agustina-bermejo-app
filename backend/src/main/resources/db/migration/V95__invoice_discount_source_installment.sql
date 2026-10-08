alter table invoice_discount add column source_installment_id bigint references scholarship_installment (id);

create index ix_invoice_discount_source_installment on invoice_discount (source_installment_id) where source_installment_id is not null;
