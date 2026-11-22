insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('billing.run_billing', 'billing', 'Ejecutar a mano la corrida nocturna de facturación del día', false, true),
    ('billing.issue_monthly_invoices_manually', 'billing', 'Emitir a mano las facturas mensuales de un período a partir de una simulación', false, true),
    ('billing.retry_draft_invoice_items', 'billing', 'Reintentar un ítem fallido de una corrida mensual de facturación', false, true),
    ('billing.approve_draft_invoices', 'billing', 'Aprobar facturas borrador de una corrida mensual para que se emitan', false, true),
    ('billing.discard_draft_invoices', 'billing', 'Descartar facturas borrador de una corrida mensual', false, true),
    ('billing.manage_discount_definition', 'billing', 'Crear y cerrar definiciones de descuento', false, true),
    ('billing.grant_student_discount', 'billing', 'Otorgar un descuento a la inscripción de un alumno', false, true),
    ('billing.revoke_student_discount', 'billing', 'Revocar un descuento otorgado a un alumno', false, true),
    ('billing.manage_promotion', 'billing', 'Crear promociones de cursos y carreras y cerrarlas', false, true),
    ('billing.propose_price_change', 'billing', 'Proponer o publicar una nueva versión de precio', false, true),
    ('billing.cancel_price_version', 'billing', 'Cancelar una versión de precio programada', false, true),
    ('billing.manage_payment_plan_options', 'billing', 'Crear opciones de plan de pago de cursos y carreras', false, true),
    ('billing.manage_year_billing_template', 'billing', 'Crear y configurar plantillas de cobro anual de una carrera', false, true),
    ('billing.manage_year_billing_cohorts', 'billing', 'Crear cohortes de una plantilla de cobro anual y cambiar su descuento de matrícula', false, true),
    ('billing.set_late_payment_settings', 'billing', 'Definir la configuración de recargos por mora', false, true),
    ('billing.manage_payment_gateway', 'billing', 'Cambiar la configuración de la pasarela de pago y subir el QR de cobro', false, true),
    ('billing.decide_authorization_request', 'billing', 'Aprobar o rechazar solicitudes de autorización de operaciones de facturación', false, true),
    ('billing.withdraw_authorization_request', 'billing', 'Retirar una solicitud de autorización de operaciones de facturación', false, true),
    ('billing.request_advance_enrollment_fee', 'billing', 'Pedir en mostrador el adelanto de la matrícula de un alumno', false, true),
    ('billing.publish_annual_upfront_terms', 'billing', 'Publicar una nueva versión de los términos del pago anual adelantado', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'billing.retry_draft_invoice_items'),
    ('ADMINISTRATIVO', 'billing.approve_draft_invoices'),
    ('ADMINISTRATIVO', 'billing.discard_draft_invoices'),
    ('ADMINISTRATIVO', 'billing.manage_discount_definition'),
    ('ADMINISTRATIVO', 'billing.grant_student_discount'),
    ('ADMINISTRATIVO', 'billing.revoke_student_discount'),
    ('ADMINISTRATIVO', 'billing.propose_price_change'),
    ('ADMINISTRATIVO', 'billing.manage_year_billing_cohorts'),
    ('ADMINISTRATIVO', 'billing.withdraw_authorization_request'),
    ('ADMINISTRATIVO', 'billing.request_advance_enrollment_fee')
) as grants (role, code)
join permission on permission.code = grants.code;
