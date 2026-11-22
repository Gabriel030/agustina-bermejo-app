insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('attendance.record', 'attendance', 'Tomar asistencia de una comisión y consultar su listado y su clase del día', false, false),
    ('attendance.cancel_class_session', 'attendance', 'Cancelar una clase de una comisión', false, true),
    ('attendance.read_section', 'attendance', 'Ver el historial y el informe de ausencias de una comisión', false, false),
    ('attendance.read_enrollment', 'attendance', 'Ver la asistencia y el riesgo de regularidad de una inscripción', false, false),
    ('attendance.export', 'attendance', 'Exportar la asistencia de una comisión', false, false),
    ('attendance.justify', 'attendance', 'Justificar una inasistencia directamente', false, true),
    ('attendance.submit_justification_proof', 'attendance', 'Adjuntar el comprobante de justificación de una inasistencia', false, false),
    ('attendance.read_justifications', 'attendance', 'Ver las justificaciones pendientes y sus comprobantes', false, false),
    ('attendance.review_justification', 'attendance', 'Aprobar o rechazar una justificación de inasistencia', false, true),
    ('attendance.request_change', 'attendance', 'Solicitar la corrección de una asistencia y ver las solicitudes propias', false, false),
    ('attendance.read_change_requests', 'attendance', 'Ver las solicitudes de corrección de asistencia', false, false),
    ('attendance.review_change_request', 'attendance', 'Aprobar o rechazar una solicitud de corrección de asistencia', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('DOCENTE', 'attendance.record'),
    ('DOCENTE', 'attendance.cancel_class_session'),
    ('DOCENTE', 'attendance.read_section'),
    ('DOCENTE', 'attendance.export'),
    ('DOCENTE', 'attendance.submit_justification_proof'),
    ('DOCENTE', 'attendance.request_change'),
    ('PRECEPTOR', 'attendance.read_section'),
    ('PRECEPTOR', 'attendance.read_enrollment'),
    ('PRECEPTOR', 'attendance.export'),
    ('PRECEPTOR', 'attendance.justify'),
    ('PRECEPTOR', 'attendance.read_justifications'),
    ('PRECEPTOR', 'attendance.review_justification'),
    ('PRECEPTOR', 'attendance.read_change_requests'),
    ('PRECEPTOR', 'attendance.review_change_request'),
    ('ADMINISTRATIVO', 'attendance.read_section'),
    ('ADMINISTRATIVO', 'attendance.read_enrollment'),
    ('ADMINISTRATIVO', 'attendance.export'),
    ('ADMINISTRATIVO', 'attendance.justify'),
    ('ADMINISTRATIVO', 'attendance.read_justifications'),
    ('ADMINISTRATIVO', 'attendance.review_justification'),
    ('ADMINISTRATIVO', 'attendance.read_change_requests'),
    ('ADMINISTRATIVO', 'attendance.review_change_request')
) as grants (role, code)
join permission on permission.code = grants.code;
