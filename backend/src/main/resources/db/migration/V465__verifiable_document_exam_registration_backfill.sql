insert into verifiable_document (code, document_type, source_id, registered_at)
select c.code, 'EXAM_REGISTRATION_CERTIFICATE', c.id, c.issued_at
from exam_registration_certificate c
where not exists (select 1 from verifiable_document v where v.code = c.code);
