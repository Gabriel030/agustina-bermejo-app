insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('platform.read_backup_status', 'platform', 'Ver el estado de las copias de seguridad', false, false),
    ('platform.read_institute_settings', 'platform', 'Ver la configuración del instituto', false, false),
    ('platform.change_payment_methods', 'platform', 'Habilitar o deshabilitar medios de pago del instituto', false, true),
    ('platform.change_billing_notifications', 'platform', 'Activar o desactivar los avisos de facturación', false, false),
    ('platform.read_institute_settings_audit', 'platform', 'Ver el historial de cambios de la configuración del instituto', false, false),
    ('platform.read_whatsapp_settings', 'platform', 'Ver la configuración de la API de WhatsApp', false, false),
    ('platform.change_whatsapp_settings', 'platform', 'Activar o desactivar la API de WhatsApp', false, true),
    ('platform.read_own_payment_methods', 'platform', 'Ver los medios de pago disponibles para uno mismo', false, false),
    ('platform.read_audit_log', 'platform', 'Ver el registro de auditoría de la plataforma', false, false),
    ('platform.read_features', 'platform', 'Ver las funcionalidades de la plataforma, sus cambios y la vista previa de una transición', false, false),
    ('platform.change_features', 'platform', 'Cambiar el estado o el modo de facturación de una funcionalidad', false, true),
    ('platform.read_provisioning_status', 'platform', 'Ver si el instituto ya tiene su primer director', false, false),
    ('platform.provision_first_director', 'platform', 'Crear el primer director de un instituto', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ALUMNO', 'platform.read_own_payment_methods'),
    ('DOCENTE', 'platform.read_own_payment_methods'),
    ('PRECEPTOR', 'platform.read_own_payment_methods'),
    ('ADMINISTRATIVO', 'platform.read_own_payment_methods'),
    ('ADMINISTRATIVO', 'platform.read_institute_settings')
) as grants (role, code)
join permission on permission.code = grants.code;
