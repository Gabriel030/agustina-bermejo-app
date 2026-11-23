insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('certificate.signers_view', 'certificate', 'Ver los firmantes de constancias y sus firmas', false, false),
    ('certificate.document_settings_view', 'certificate', 'Ver la configuración de emisión de constancias de examen y de materias aprobadas', false, false),
    ('certificate.document_settings_manage', 'certificate', 'Cambiar la configuración de emisión de constancias de examen y de materias aprobadas', false, true),
    ('certificate.regular_settings_view', 'certificate', 'Ver la configuración de las constancias de alumno regular', false, false),
    ('certificate.regular_settings_manage', 'certificate', 'Cambiar la configuración de las constancias de alumno regular', false, true);

update permission set sensitive = true where code = 'certificate.signers_manage';

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'certificate.signers_view'),
    ('ADMINISTRATIVO', 'certificate.document_settings_view'),
    ('ADMINISTRATIVO', 'certificate.regular_settings_view')
) as grants (role, code)
join permission on permission.code = grants.code;
