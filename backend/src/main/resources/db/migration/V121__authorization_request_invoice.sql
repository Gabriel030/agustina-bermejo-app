create table authorization_request_invoice (
    authorization_request_id bigint not null references authorization_request (id),
    invoice_id bigint not null references invoice (id),
    primary key (authorization_request_id, invoice_id)
);

create index authorization_request_invoice_invoice_idx on authorization_request_invoice (invoice_id);

insert into authorization_request_invoice (authorization_request_id, invoice_id)
select ar.id, (ar.operation_payload ->> 'invoiceId')::bigint
from authorization_request ar
where ar.operation_type = 'CANCEL_INVOICE'
  and ar.operation_payload ->> 'invoiceId' is not null
  and exists (select 1 from invoice i where i.id = (ar.operation_payload ->> 'invoiceId')::bigint)
on conflict do nothing;
