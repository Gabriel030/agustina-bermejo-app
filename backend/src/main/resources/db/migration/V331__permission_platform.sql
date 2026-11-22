insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('platform.read_institute_settings', 'platform', 'Ver la configuración del instituto', false, false),
    ('platform.change_payment_methods', 'platform', 'Habilitar o deshabilitar medios de pago del instituto', false, true),
    ('platform.change_billing_notifications', 'platform', 'Activar o desactivar los avisos de facturación', false, false),
    ('platform.read_institute_settings_audit', 'platform', 'Ver el historial de cambios de la configuración del instituto', false, false),
    ('platform.read_whatsapp_settings', 'platform', 'Ver la configuración de la API de WhatsApp', false, false),
    ('platform.change_whatsapp_settings', 'platform', 'Activar o desactivar la API de WhatsApp', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'platform.read_institute_settings')
) as grants (role, code)
join permission on permission.code = grants.code;
