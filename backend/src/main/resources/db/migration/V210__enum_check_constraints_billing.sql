alter table authorization_request
    add constraint authorization_request_operation_type_enum check (operation_type in ('ACQUIRE_COHORT_PROMOTION', 'ALLOCATE_ACCOUNT_CREDIT', 'APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT', 'APPROVE_DRAFT_INVOICES', 'APPROVE_PENDING_BANK_TRANSFER_PAYMENT', 'CANCEL_CONFIGURATION_VERSION', 'CANCEL_INVOICE', 'CANCEL_INVOICES_AFTER_WITHDRAWAL', 'CANCEL_PRICE_VERSION', 'CHANGE_BILLING_CONFIGURATION', 'CHANGE_PRICE', 'CLOSE_DISCOUNT_DEFINITION', 'COMPLETE_REFUND', 'CREATE_DISCOUNT_DEFINITION', 'CREATE_LATE_PAYMENT_SETTINGS', 'DISCARD_ANNUAL_UPFRONT_REQUEST', 'FAIL_REFUND', 'GRANT_STUDENT_DISCOUNT', 'ISSUE_CREDIT_NOTE', 'ISSUE_INVOICE', 'ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE', 'ISSUE_LATE_FEE_DEBIT_NOTE', 'ISSUE_REFUND', 'REGISTER_ANNUAL_UPFRONT_CASH_PAYMENT', 'REGISTER_CHARGEBACK', 'REGISTER_GATEWAY_PAYMENT', 'REGISTER_PAYMENT', 'REJECT_PENDING_BANK_TRANSFER_PAYMENT', 'REQUEST_ANNUAL_UPFRONT', 'REVERSE_CHARGEBACK', 'REVOKE_BANK_TRANSFER_APPROVAL', 'REVOKE_STUDENT_DISCOUNT', 'SUBMIT_BANK_TRANSFER_RECEIPT')) not valid,
    add constraint authorization_request_required_role_enum check (required_role in ('DIRECTOR', 'DIRECTOR_OR_ADMINISTRATIVE')) not valid;

alter table authorization_request_status_change
    add constraint authorization_request_status_change_event_enum check (event in ('APPROVE', 'EXPIRE', 'OPEN', 'REJECT', 'WITHDRAW')) not valid,
    add constraint authorization_request_status_change_from_status_enum check (from_status in ('APPROVED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN')) not valid,
    add constraint authorization_request_status_change_to_status_enum check (to_status in ('APPROVED', 'EXPIRED', 'PENDING', 'REJECTED', 'WITHDRAWN')) not valid;

alter table bank_transfer_receipt
    add constraint bank_transfer_receipt_declared_amount_currency_enum check (declared_amount_currency in ('ARS', 'USD')) not valid,
    add constraint bank_transfer_receipt_status_enum check (status in ('APPLIED_TO_DEBT', 'APPROVED', 'PENDING_REVIEW', 'REJECTED')) not valid;

alter table billing_run
    add constraint billing_run_step_enum check (step in ('ACCOUNT_CONSISTENCY', 'ACCOUNT_CREDIT_ALLOCATION', 'ADVANCE_ENROLLMENT_FEE_EXPIRY', 'ANNUAL_UPFRONT_EXPIRY', 'COLLECTIONS_SNAPSHOT', 'COLLECTION_REMINDER_PLANNING', 'ENROLLMENT_BILLING_COMPLIANCE', 'GATEWAY_SYNC', 'LATE_FEE_ACCRUAL', 'LATE_FEE_WITHDRAWAL_CORRECTION', 'OVERDUE_DETECTION', 'TERM_END_WITHDRAWAL')) not valid;

alter table billing_run_item
    add constraint billing_run_item_subject_type_enum check (subject_type in ('ANNUAL_UPFRONT_REQUEST', 'COURSE_ENROLLMENT', 'CURRENT_ACCOUNT', 'INVOICE', 'LATE_FEE_CHARGE', 'PROGRAM_ENROLLMENT', 'STUDENT')) not valid;

alter table chargeback
    add constraint chargeback_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid;

alter table configuration_version_status_change
    add constraint configuration_version_status_change_event_enum check (event in ('APPROVE_FOR_FUTURE', 'APPROVE_FOR_TODAY', 'CANCEL', 'EXPIRE', 'PROPOSE', 'PUBLISH_FOR_FUTURE', 'PUBLISH_FOR_TODAY', 'REACH_START', 'REJECT', 'SUPERSEDE', 'WITHDRAW')) not valid,
    add constraint configuration_version_status_change_from_status_enum check (from_status in ('CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN')) not valid,
    add constraint configuration_version_status_change_to_status_enum check (to_status in ('CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN')) not valid;

alter table credit_note
    add constraint credit_note_free_credit_amount_currency_enum check (free_credit_amount_currency in ('ARS', 'USD')) not valid,
    add constraint credit_note_offset_amount_currency_enum check (offset_amount_currency in ('ARS', 'USD')) not valid,
    add constraint credit_note_total_amount_currency_enum check (total_amount_currency in ('ARS', 'USD')) not valid;

alter table credit_note_line
    add constraint credit_note_line_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid,
    add constraint credit_note_line_free_part_currency_enum check (free_part_currency in ('ARS', 'USD')) not valid,
    add constraint credit_note_line_offset_part_currency_enum check (offset_part_currency in ('ARS', 'USD')) not valid;

alter table current_account_entry
    add constraint current_account_entry_allocatable_amount_currency_enum check (allocatable_amount_currency in ('ARS', 'USD')) not valid,
    add constraint current_account_entry_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid,
    add constraint current_account_entry_currency_enum check (currency in ('ARS', 'USD')) not valid,
    add constraint current_account_entry_running_balance_amount_currency_enum check (running_balance_amount_currency in ('ARS', 'USD')) not valid;

alter table discount_definition_status_change
    add constraint discount_definition_status_change_event_enum check (event in ('DEFINITION_CLOSED', 'DEFINITION_CREATED')) not valid,
    add constraint discount_definition_status_change_from_status_enum check (from_status in ('ACTIVE', 'CLOSED')) not valid,
    add constraint discount_definition_status_change_to_status_enum check (to_status in ('ACTIVE', 'CLOSED')) not valid;

alter table invoice
    add constraint invoice_credited_amount_currency_enum check (credited_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_discounts_total_currency_enum check (discounts_total_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_lines_total_currency_enum check (lines_total_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_outstanding_amount_currency_enum check (outstanding_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_paid_amount_currency_enum check (paid_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_total_currency_enum check (total_currency in ('ARS', 'USD')) not valid;

alter table invoice_discount
    add constraint invoice_discount_base_amount_currency_enum check (base_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_discount_discount_amount_currency_enum check (discount_amount_currency in ('ARS', 'USD')) not valid;

alter table invoice_line
    add constraint invoice_line_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_line_credited_amount_currency_enum check (credited_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_line_currency_enum check (currency in ('ARS', 'USD')) not valid,
    add constraint invoice_line_discount_amount_currency_enum check (discount_amount_currency in ('ARS', 'USD')) not valid,
    add constraint invoice_line_free_credited_amount_currency_enum check (free_credited_amount_currency in ('ARS', 'USD')) not valid;

alter table monthly_invoice_simulation_line
    add constraint monthly_invoice_simulation_line_total_currency_enum check (total_currency in ('ARS', 'USD')) not valid;

alter table payment
    add constraint payment_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid,
    add constraint payment_approved_amount_currency_enum check (approved_amount_currency in ('ARS', 'USD')) not valid,
    add constraint payment_refunded_amount_currency_enum check (refunded_amount_currency in ('ARS', 'USD')) not valid;

alter table payment_checkout_item
    add constraint payment_checkout_item_outstanding_at_selection_currency_enum check (outstanding_at_selection_currency in ('ARS', 'USD')) not valid;

alter table payment_plan_option
    add constraint payment_plan_option_cadence_enum check (cadence in ('ANNUAL_UPFRONT', 'MONTHLY')) not valid;

alter table price_version_status_change
    add constraint price_version_status_change_event_enum check (event in ('APPROVE_FOR_FUTURE', 'APPROVE_FOR_TODAY', 'CANCEL', 'EXPIRE', 'PROPOSE', 'PUBLISH_FOR_FUTURE', 'PUBLISH_FOR_TODAY', 'REACH_START', 'REJECT', 'SUPERSEDE', 'WITHDRAW')) not valid,
    add constraint price_version_status_change_from_status_enum check (from_status in ('CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN')) not valid,
    add constraint price_version_status_change_to_status_enum check (to_status in ('CANCELLED', 'EFFECTIVE', 'EXPIRED', 'PROPOSED', 'REJECTED', 'SCHEDULED', 'SUPERSEDED', 'WITHDRAWN')) not valid;

alter table refund
    add constraint refund_amount_currency_enum check (amount_currency in ('ARS', 'USD')) not valid;

alter table student_current_account
    add constraint student_current_account_balance_amount_currency_enum check (balance_amount_currency in ('ARS', 'USD')) not valid,
    add constraint student_current_account_unallocated_credit_amount_currency_enum check (unallocated_credit_amount_currency in ('ARS', 'USD')) not valid;

alter table student_discount_status_change
    add constraint student_discount_status_change_event_enum check (event in ('CONSUMPTION_RETURNED', 'DISCOUNT_GRANTED', 'DISCOUNT_REVOKED', 'ENROLLMENT_ENDED', 'LAST_INSTALLMENT_CONSUMED')) not valid,
    add constraint student_discount_status_change_from_status_enum check (from_status in ('ACTIVE', 'ENDED', 'EXHAUSTED', 'REVOKED')) not valid,
    add constraint student_discount_status_change_to_status_enum check (to_status in ('ACTIVE', 'ENDED', 'EXHAUSTED', 'REVOKED')) not valid;

alter table authorization_request validate constraint authorization_request_operation_type_enum;
alter table authorization_request validate constraint authorization_request_required_role_enum;
alter table authorization_request_status_change validate constraint authorization_request_status_change_event_enum;
alter table authorization_request_status_change validate constraint authorization_request_status_change_from_status_enum;
alter table authorization_request_status_change validate constraint authorization_request_status_change_to_status_enum;
alter table bank_transfer_receipt validate constraint bank_transfer_receipt_declared_amount_currency_enum;
alter table bank_transfer_receipt validate constraint bank_transfer_receipt_status_enum;
alter table billing_run validate constraint billing_run_step_enum;
alter table billing_run_item validate constraint billing_run_item_subject_type_enum;
alter table chargeback validate constraint chargeback_amount_currency_enum;
alter table configuration_version_status_change validate constraint configuration_version_status_change_event_enum;
alter table configuration_version_status_change validate constraint configuration_version_status_change_from_status_enum;
alter table configuration_version_status_change validate constraint configuration_version_status_change_to_status_enum;
alter table credit_note validate constraint credit_note_free_credit_amount_currency_enum;
alter table credit_note validate constraint credit_note_offset_amount_currency_enum;
alter table credit_note validate constraint credit_note_total_amount_currency_enum;
alter table credit_note_line validate constraint credit_note_line_amount_currency_enum;
alter table credit_note_line validate constraint credit_note_line_free_part_currency_enum;
alter table credit_note_line validate constraint credit_note_line_offset_part_currency_enum;
alter table current_account_entry validate constraint current_account_entry_allocatable_amount_currency_enum;
alter table current_account_entry validate constraint current_account_entry_amount_currency_enum;
alter table current_account_entry validate constraint current_account_entry_currency_enum;
alter table current_account_entry validate constraint current_account_entry_running_balance_amount_currency_enum;
alter table discount_definition_status_change validate constraint discount_definition_status_change_event_enum;
alter table discount_definition_status_change validate constraint discount_definition_status_change_from_status_enum;
alter table discount_definition_status_change validate constraint discount_definition_status_change_to_status_enum;
alter table invoice validate constraint invoice_credited_amount_currency_enum;
alter table invoice validate constraint invoice_discounts_total_currency_enum;
alter table invoice validate constraint invoice_lines_total_currency_enum;
alter table invoice validate constraint invoice_outstanding_amount_currency_enum;
alter table invoice validate constraint invoice_paid_amount_currency_enum;
alter table invoice validate constraint invoice_total_currency_enum;
alter table invoice_discount validate constraint invoice_discount_base_amount_currency_enum;
alter table invoice_discount validate constraint invoice_discount_discount_amount_currency_enum;
alter table invoice_line validate constraint invoice_line_amount_currency_enum;
alter table invoice_line validate constraint invoice_line_credited_amount_currency_enum;
alter table invoice_line validate constraint invoice_line_currency_enum;
alter table invoice_line validate constraint invoice_line_discount_amount_currency_enum;
alter table invoice_line validate constraint invoice_line_free_credited_amount_currency_enum;
alter table monthly_invoice_simulation_line validate constraint monthly_invoice_simulation_line_total_currency_enum;
alter table payment validate constraint payment_amount_currency_enum;
alter table payment validate constraint payment_approved_amount_currency_enum;
alter table payment validate constraint payment_refunded_amount_currency_enum;
alter table payment_checkout_item validate constraint payment_checkout_item_outstanding_at_selection_currency_enum;
alter table payment_plan_option validate constraint payment_plan_option_cadence_enum;
alter table price_version_status_change validate constraint price_version_status_change_event_enum;
alter table price_version_status_change validate constraint price_version_status_change_from_status_enum;
alter table price_version_status_change validate constraint price_version_status_change_to_status_enum;
alter table refund validate constraint refund_amount_currency_enum;
alter table student_current_account validate constraint student_current_account_balance_amount_currency_enum;
alter table student_current_account validate constraint student_current_account_unallocated_credit_amount_currency_enum;
alter table student_discount_status_change validate constraint student_discount_status_change_event_enum;
alter table student_discount_status_change validate constraint student_discount_status_change_from_status_enum;
alter table student_discount_status_change validate constraint student_discount_status_change_to_status_enum;
