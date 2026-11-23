update permission set fixed_to_director = true
where code in (
    'billing.decide_authorization_request',
    'billing.run_billing',
    'billing.issue_monthly_invoices_manually',
    'billing.manage_promotion',
    'billing.cancel_price_version',
    'billing.manage_payment_plan_options',
    'billing.manage_year_billing_template',
    'billing.set_late_payment_settings',
    'billing.manage_payment_gateway',
    'billing.publish_annual_upfront_terms',
    'billing.revoke_payment_approval');

delete from role_permission
where role <> 'DIRECTOR'
  and permission_id in (select id from permission where fixed_to_director and code like 'billing.%');

update permission set description = description || '. Solo el Director; no se puede delegar'
where fixed_to_director and code like 'billing.%';

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
