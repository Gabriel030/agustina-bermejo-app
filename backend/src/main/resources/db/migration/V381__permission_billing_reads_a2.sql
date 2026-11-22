insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('billing.read_student_billing', 'billing', 'Ver la facturación de un alumno: facturas de una inscripción y su desglose de precio, pagos, saldo a favor, notas de crédito, reembolsos y pedidos de reembolso', false, false),
    ('billing.read_bank_transfer_receipts', 'billing', 'Ver los comprobantes de transferencia pendientes de revisión y descargar su archivo', false, false),
    ('billing.read_prices', 'billing', 'Ver los precios vigentes y el historial de versiones de cada precio', false, false),
    ('billing.preview_price_change', 'billing', 'Simular el impacto de un cambio de precio y validarlo sin guardarlo', false, false),
    ('billing.read_discounts', 'billing', 'Ver las definiciones de descuento, las promociones, los descuentos otorgados a alumnos y el informe de beneficios perdidos', false, false),
    ('billing.validate_discount_definition', 'billing', 'Validar el borrador de una definición de descuento sin guardarla', false, false),
    ('billing.preview_student_discount', 'billing', 'Simular el otorgamiento de un descuento a una inscripción sin guardarlo', false, false),
    ('billing.preview_payment_reversal', 'billing', 'Simular el registro o la reversión de un contracargo sin guardarlo', false, false),
    ('billing.preview_approval_revocation', 'billing', 'Simular la revocación de la aprobación de un pago por transferencia sin guardarla', false, false),
    ('billing.preview_cash_payment', 'billing', 'Simular un pago en efectivo de un alumno sin registrarlo', false, false),
    ('billing.preview_refund', 'billing', 'Simular un pedido de reembolso de un alumno y ver si es elegible, sin registrarlo', false, false),
    ('billing.preview_invoice_cancellation', 'billing', 'Simular la anulación de una factura sin ejecutarla', false, false),
    ('billing.read_authorization_requests', 'billing', 'Ver las solicitudes de autorización de operaciones de facturación y su detalle', false, false),
    ('billing.read_annual_upfront', 'billing', 'Ver la cotización del pago anual adelantado de una inscripción y las solicitudes de pago anual', false, false),
    ('billing.read_annual_upfront_terms_versions', 'billing', 'Ver el historial de versiones de los términos del pago anual adelantado', false, false),
    ('billing.read_year_billing_templates', 'billing', 'Ver las plantillas de cobro anual de una carrera', false, false);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'billing.read_student_billing'),
    ('ADMINISTRATIVO', 'billing.read_bank_transfer_receipts'),
    ('ADMINISTRATIVO', 'billing.read_prices'),
    ('ADMINISTRATIVO', 'billing.preview_price_change'),
    ('ADMINISTRATIVO', 'billing.read_discounts'),
    ('ADMINISTRATIVO', 'billing.validate_discount_definition'),
    ('ADMINISTRATIVO', 'billing.preview_student_discount'),
    ('ADMINISTRATIVO', 'billing.preview_payment_reversal'),
    ('ADMINISTRATIVO', 'billing.preview_cash_payment'),
    ('ADMINISTRATIVO', 'billing.preview_refund'),
    ('ADMINISTRATIVO', 'billing.preview_invoice_cancellation'),
    ('ADMINISTRATIVO', 'billing.read_authorization_requests'),
    ('ADMINISTRATIVO', 'billing.read_annual_upfront'),
    ('ADMINISTRATIVO', 'billing.read_year_billing_templates')
) as grants (role, code)
join permission on permission.code = grants.code;
