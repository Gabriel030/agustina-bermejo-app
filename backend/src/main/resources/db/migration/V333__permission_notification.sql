insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('notification.read_collection_reminder_metrics', 'notification', 'Ver las métricas de los recordatorios de cobranza', false, false),
    ('notification.read_simulated_deliveries', 'notification', 'Ver los correos simulados enviados por el sistema', false, false),
    ('notification.compose_broadcast', 'notification', 'Armar un envío masivo: segmentos, canales, destinatarios estimados y borrador', false, false),
    ('notification.read_broadcasts', 'notification', 'Ver los envíos masivos, su detalle y sus destinatarios', false, false),
    ('notification.confirm_broadcast', 'notification', 'Confirmar un envío masivo y disparar su envío a los destinatarios', false, true),
    ('notification.cancel_broadcast', 'notification', 'Cancelar un envío masivo confirmado', false, true),
    ('notification.read_message_templates', 'notification', 'Ver las plantillas de mensajes y su vista previa', false, false),
    ('notification.create_message_template', 'notification', 'Crear una plantilla de mensajes', false, true),
    ('notification.update_message_template', 'notification', 'Modificar una plantilla de mensajes', false, true),
    ('notification.deactivate_message_template', 'notification', 'Dar de baja una plantilla de mensajes', false, true),
    ('notification.read_whatsapp_templates', 'notification', 'Ver las plantillas de WhatsApp vinculadas', false, false),
    ('notification.create_whatsapp_template', 'notification', 'Vincular una plantilla de WhatsApp aprobada por el proveedor', false, true),
    ('notification.update_whatsapp_template', 'notification', 'Modificar el vínculo de una plantilla de WhatsApp', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'notification.read_collection_reminder_metrics'),
    ('ADMINISTRATIVO', 'notification.read_simulated_deliveries'),
    ('ADMINISTRATIVO', 'notification.compose_broadcast'),
    ('ADMINISTRATIVO', 'notification.read_broadcasts'),
    ('ADMINISTRATIVO', 'notification.confirm_broadcast'),
    ('ADMINISTRATIVO', 'notification.cancel_broadcast'),
    ('ADMINISTRATIVO', 'notification.read_message_templates'),
    ('ADMINISTRATIVO', 'notification.create_message_template'),
    ('ADMINISTRATIVO', 'notification.update_message_template'),
    ('ADMINISTRATIVO', 'notification.deactivate_message_template')
) as grants (role, code)
join permission on permission.code = grants.code;
