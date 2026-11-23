insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('notification.read_invitation_waves', 'notification', 'Ver las oleadas de invitación a alumnos importados, su avance y su reporte por persona', false, false),
    ('notification.manage_invitation_waves', 'notification', 'Armar, pausar, reanudar, cancelar y reintentar las oleadas de invitación a alumnos importados', false, false),
    ('notification.send_invitation_wave', 'notification', 'Iniciar una oleada de invitación y disparar los correos de activación a los alumnos importados', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'notification.read_invitation_waves'),
    ('ADMINISTRATIVO', 'notification.manage_invitation_waves'),
    ('ADMINISTRATIVO', 'notification.send_invitation_wave')
) as grants (role, code)
join permission on permission.code = grants.code;
