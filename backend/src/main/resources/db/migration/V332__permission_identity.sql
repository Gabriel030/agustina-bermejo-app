insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('identity.read_staff', 'identity', 'Ver el listado y el legajo del personal', false, false),
    ('identity.create_staff', 'identity', 'Dar de alta a un integrante del personal', false, true),
    ('identity.update_staff', 'identity', 'Modificar los datos y el rol de un integrante del personal', false, true),
    ('identity.withdraw_staff', 'identity', 'Dar de baja a un integrante del personal', false, true),
    ('identity.reactivate_staff', 'identity', 'Reactivar a un integrante del personal dado de baja', false, true),
    ('identity.reset_staff_password', 'identity', 'Restablecer la contraseña de un integrante del personal', false, true),
    ('identity.read_students', 'identity', 'Ver el padrón y el legajo de los alumnos', false, false),
    ('identity.withdraw_student', 'identity', 'Dar de baja a un alumno', false, true),
    ('identity.reactivate_student', 'identity', 'Reactivar a un alumno dado de baja', false, true),
    ('identity.reset_student_password', 'identity', 'Restablecer la contraseña de un alumno', false, true),
    ('identity.read_academic_settings', 'identity', 'Ver la configuración académica de la institución', false, false),
    ('identity.update_academic_settings', 'identity', 'Modificar la configuración académica de la institución', false, false),
    ('identity.read_legal_acceptances', 'identity', 'Ver las aceptaciones de documentos legales', false, false);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'identity.read_staff'),
    ('ADMINISTRATIVO', 'identity.create_staff'),
    ('ADMINISTRATIVO', 'identity.update_staff'),
    ('ADMINISTRATIVO', 'identity.withdraw_staff'),
    ('ADMINISTRATIVO', 'identity.reactivate_staff'),
    ('ADMINISTRATIVO', 'identity.reset_staff_password'),
    ('PRECEPTOR', 'identity.read_students'),
    ('ADMINISTRATIVO', 'identity.read_students'),
    ('ADMINISTRATIVO', 'identity.withdraw_student'),
    ('ADMINISTRATIVO', 'identity.reactivate_student'),
    ('PRECEPTOR', 'identity.reset_student_password'),
    ('ADMINISTRATIVO', 'identity.reset_student_password'),
    ('DOCENTE', 'identity.read_academic_settings'),
    ('PRECEPTOR', 'identity.read_academic_settings'),
    ('ADMINISTRATIVO', 'identity.read_academic_settings'),
    ('ADMINISTRATIVO', 'identity.read_legal_acceptances')
) as grants (role, code)
join permission on permission.code = grants.code;
