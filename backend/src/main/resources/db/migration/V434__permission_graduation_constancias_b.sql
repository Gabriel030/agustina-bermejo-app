insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('certificate.exceptions_view', 'certificate', 'Ver las excepciones de constancias y su detalle', false, false),
    ('certificate.exceptions_request', 'certificate', 'Pedir una excepción de constancia al Director', false, false),
    ('certificate.exceptions_withdraw', 'certificate', 'Retirar un pedido de excepción de constancia', false, false),
    ('certificate.exceptions_create_direct', 'certificate', 'Crear una excepción de constancia ya aprobada, sin pedido previo', false, true),
    ('certificate.exceptions_approve', 'certificate', 'Aprobar una excepción de constancia', false, true),
    ('certificate.exceptions_reject', 'certificate', 'Rechazar una excepción de constancia', false, true),
    ('certificate.regular_view', 'certificate', 'Ver las constancias de alumno regular emitidas y la regularidad de un alumno', false, false),
    ('certificate.regular_requests_view', 'certificate', 'Ver los pedidos de constancia de alumno regular', false, false),
    ('certificate.regular_requests_reject', 'certificate', 'Rechazar un pedido de constancia de alumno regular', false, false),
    ('certificate.exam_candidates_view', 'certificate', 'Ver los alumnos que pueden recibir constancia de examen en una mesa', false, false),
    ('certificate.exam_issue_batch', 'certificate', 'Emitir en lote las constancias de examen de una mesa', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'certificate.exceptions_view'),
    ('ADMINISTRATIVO', 'certificate.exceptions_request'),
    ('ADMINISTRATIVO', 'certificate.exceptions_withdraw'),
    ('ADMINISTRATIVO', 'certificate.regular_view'),
    ('ADMINISTRATIVO', 'certificate.regular_requests_view'),
    ('ADMINISTRATIVO', 'certificate.regular_requests_reject'),
    ('ADMINISTRATIVO', 'certificate.exam_candidates_view'),
    ('ADMINISTRATIVO', 'certificate.exam_issue_batch'),
    ('DOCENTE', 'certificate.exam_candidates_view'),
    ('DOCENTE', 'certificate.exam_issue_batch')
) as grants (role, code)
join permission on permission.code = grants.code;
