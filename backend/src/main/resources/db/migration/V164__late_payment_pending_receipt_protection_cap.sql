update configuration_version
set config_values = config_values || jsonb_build_object('pendingReceiptProtectionBusinessDays', 10)
where scope = 'LATE_PAYMENT' and not (config_values ? 'pendingReceiptProtectionBusinessDays');
