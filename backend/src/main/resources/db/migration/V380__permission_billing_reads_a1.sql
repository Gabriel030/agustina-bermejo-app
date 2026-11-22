insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('billing.read_billing_metrics', 'billing', 'Ver las métricas mensuales de facturación y su primer mes', false, false),
    ('billing.read_financial_dashboard', 'billing', 'Ver el tablero financiero', false, false),
    ('billing.read_debtors', 'billing', 'Ver el listado paginado de deudores del tablero financiero', false, false),
    ('billing.read_student_billing_overview', 'billing', 'Ver el estado de cobro de las inscripciones de un alumno', false, false),
    ('billing.read_payment_standing', 'billing', 'Ver la situación de pago de una inscripción a carrera o a curso', false, false),
    ('billing.read_payment_standing_history', 'billing', 'Ver el historial de la situación de pago de una inscripción', false, false),
    ('billing.read_overdue_contacts', 'billing', 'Ver la lista de contactos con cuotas vencidas', false, false),
    ('billing.preview_reminder_impact', 'billing', 'Simular el impacto de un cambio en los recordatorios de cobranza', false, false),
    ('billing.read_reminder_config', 'billing', 'Ver la configuración de recordatorios de cobranza, sus cronogramas y su historial de versiones', false, false),
    ('billing.manage_reminder_config', 'billing', 'Validar y proponer cambios en la configuración y los cronogramas de recordatorios de cobranza', false, false),
    ('billing.read_billing_runs', 'billing', 'Ver las corridas de facturación, sus alertas, los borradores mensuales, las alertas de configuración y simular una corrida mensual', false, false),
    ('billing.read_late_payment_settings', 'billing', 'Ver la configuración vigente de recargos por mora', false, false);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'billing.read_billing_metrics'),
    ('ADMINISTRATIVO', 'billing.read_financial_dashboard'),
    ('ADMINISTRATIVO', 'billing.read_student_billing_overview'),
    ('ADMINISTRATIVO', 'billing.read_payment_standing'),
    ('PRECEPTOR', 'billing.read_payment_standing'),
    ('ADMINISTRATIVO', 'billing.read_payment_standing_history'),
    ('ADMINISTRATIVO', 'billing.read_overdue_contacts'),
    ('ADMINISTRATIVO', 'billing.preview_reminder_impact'),
    ('ADMINISTRATIVO', 'billing.read_reminder_config'),
    ('ADMINISTRATIVO', 'billing.manage_reminder_config'),
    ('ADMINISTRATIVO', 'billing.read_billing_runs'),
    ('ADMINISTRATIVO', 'billing.read_late_payment_settings')
) as grants (role, code)
join permission on permission.code = grants.code;
