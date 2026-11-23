insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('billing.register_cash_payment', 'billing', 'Registrar un pago en efectivo de un alumno', false, true),
    ('billing.register_annual_upfront_cash_payment', 'billing', 'Registrar el pago en efectivo de un pedido de pago anual adelantado', false, true),
    ('billing.submit_bank_transfer_receipt', 'billing', 'Cargar un comprobante de transferencia en nombre de un alumno', false, true),
    ('billing.review_bank_transfer_receipt', 'billing', 'Aprobar o rechazar un comprobante de transferencia bancaria', false, true),
    ('billing.revoke_payment_approval', 'billing', 'Revocar la aprobación de un pago por transferencia', false, true),
    ('billing.register_chargeback', 'billing', 'Registrar un contracargo sobre un pago', false, true),
    ('billing.reverse_chargeback', 'billing', 'Revertir un contracargo registrado', false, true),
    ('billing.request_refund', 'billing', 'Pedir un reembolso en nombre de un alumno', false, true),
    ('billing.complete_refund', 'billing', 'Marcar un reembolso como completado', false, true),
    ('billing.fail_refund', 'billing', 'Marcar un reembolso como fallido', false, true),
    ('billing.issue_credit_note', 'billing', 'Emitir o pedir autorización de una nota de crédito sobre una factura', false, true),
    ('billing.cancel_invoice', 'billing', 'Anular una factura o pedir autorización para anularla', false, true),
    ('billing.allocate_account_credit', 'billing', 'Aplicar el saldo a favor de un alumno a sus cuotas pendientes', false, true);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'billing.register_cash_payment'),
    ('ADMINISTRATIVO', 'billing.register_annual_upfront_cash_payment'),
    ('ADMINISTRATIVO', 'billing.submit_bank_transfer_receipt'),
    ('ADMINISTRATIVO', 'billing.review_bank_transfer_receipt'),
    ('ADMINISTRATIVO', 'billing.register_chargeback'),
    ('ADMINISTRATIVO', 'billing.reverse_chargeback'),
    ('ADMINISTRATIVO', 'billing.request_refund'),
    ('ADMINISTRATIVO', 'billing.complete_refund'),
    ('ADMINISTRATIVO', 'billing.fail_refund'),
    ('ADMINISTRATIVO', 'billing.issue_credit_note'),
    ('ADMINISTRATIVO', 'billing.cancel_invoice'),
    ('ADMINISTRATIVO', 'billing.allocate_account_credit')
) as grants (role, code)
join permission on permission.code = grants.code;
