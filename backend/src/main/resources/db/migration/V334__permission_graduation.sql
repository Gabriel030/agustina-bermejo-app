insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('graduation.read_requests', 'graduation', 'Ver las solicitudes de egreso de los alumnos', false, false),
    ('graduation.read_debt_summary', 'graduation', 'Ver el resumen de deuda de una solicitud de egreso', false, false),
    ('graduation.verify_debt', 'graduation', 'Verificar la deuda de una solicitud de egreso', false, false),
    ('graduation.approve_request', 'graduation', 'Aprobar una solicitud de egreso', false, true),
    ('graduation.reject_request', 'graduation', 'Rechazar una solicitud de egreso', false, true),
    ('graduation.read_certificates', 'graduation', 'Ver los certificados de egreso emitidos y su detalle', false, false),
    ('graduation.reissue_certificate', 'graduation', 'Reemitir un certificado de egreso revocado', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'graduation.read_requests'),
    ('ADMINISTRATIVO', 'graduation.read_debt_summary'),
    ('ADMINISTRATIVO', 'graduation.verify_debt'),
    ('ADMINISTRATIVO', 'graduation.reject_request'),
    ('ADMINISTRATIVO', 'graduation.read_certificates')
) as grants (role, code)
join permission on permission.code = grants.code;

update permission set sensitive = true where code in ('certificate.revoke', 'certificate.issue', 'certificate.templates_manage');
