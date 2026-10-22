insert into authorization_request_invoice (authorization_request_id, invoice_id)
select ar.id, (ar.operation_payload ->> 'invoiceId')::bigint
from authorization_request ar
where ar.operation_type = 'ISSUE_CREDIT_NOTE'
  and ar.operation_payload ->> 'invoiceId' is not null
  and exists (select 1 from invoice i where i.id = (ar.operation_payload ->> 'invoiceId')::bigint)
on conflict do nothing;
