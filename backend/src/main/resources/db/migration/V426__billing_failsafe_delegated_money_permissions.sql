update permission set fixed_to_director = true where code = 'billing.decide_authorization_request';

delete from role_permission
where role <> 'DIRECTOR'
  and permission_id = (select id from permission where code = 'billing.decide_authorization_request');

update permission set description = description || '. Un rol con este permiso delegado crea una solicitud que el Director debe aprobar'
where code in (
    'billing.manage_discount_definition',
    'billing.grant_student_discount',
    'billing.revoke_student_discount',
    'billing.propose_price_change',
    'billing.request_refund',
    'billing.issue_credit_note',
    'billing.cancel_invoice');

update permission set description = description || '. Un rol con este permiso delegado no puede registrar pagos de más de 5 días atrás: solo el Director'
where code = 'billing.register_cash_payment';

update permission set description = description || '. Un rol con este permiso delegado crea una solicitud que el Director debe aprobar si el descuento de matrícula se aparta del predeterminado'
where code = 'billing.manage_year_billing_cohorts';

update permission set description = description || '. Un rol con este permiso delegado crea una solicitud que el Director debe aprobar al cambiar la configuración; crear la plantilla se aplica directo'
where code = 'billing.manage_year_billing_template';
