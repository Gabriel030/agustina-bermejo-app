package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.BillingOperationType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthorizationOperationCatalog {

    private AuthorizationOperationCatalog() {
    }

    public static AuthorizationOperationFamily familyOf(BillingOperationType type) {
        return switch (type) {
            case ISSUE_REFUND, COMPLETE_REFUND, FAIL_REFUND -> AuthorizationOperationFamily.REFUNDS;
            case REQUEST_ANNUAL_UPFRONT, DISCARD_ANNUAL_UPFRONT_REQUEST, ISSUE_INVOICE, APPROVE_DRAFT_INVOICES, ISSUE_CREDIT_NOTE, ISSUE_LATE_FEE_DEBIT_NOTE, ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE, CANCEL_INVOICE, CANCEL_INVOICES_AFTER_WITHDRAWAL ->
                    AuthorizationOperationFamily.INVOICES_AND_NOTES;
            case ALLOCATE_ACCOUNT_CREDIT, REGISTER_PAYMENT, REGISTER_GATEWAY_PAYMENT, APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT, SUBMIT_BANK_TRANSFER_RECEIPT,
                 APPROVE_PENDING_BANK_TRANSFER_PAYMENT, REJECT_PENDING_BANK_TRANSFER_PAYMENT, REVOKE_BANK_TRANSFER_APPROVAL, REGISTER_CHARGEBACK, REVERSE_CHARGEBACK ->
                    AuthorizationOperationFamily.PAYMENTS_AND_CHARGEBACKS;
            case CREATE_DISCOUNT_DEFINITION, CLOSE_DISCOUNT_DEFINITION, GRANT_STUDENT_DISCOUNT, REVOKE_STUDENT_DISCOUNT, ACQUIRE_COHORT_PROMOTION, CHANGE_PRICE,
                 CANCEL_PRICE_VERSION -> AuthorizationOperationFamily.DISCOUNTS_AND_PRICES;
            case CREATE_LATE_PAYMENT_SETTINGS, CHANGE_BILLING_CONFIGURATION, CANCEL_CONFIGURATION_VERSION -> AuthorizationOperationFamily.CONFIGURATION;
        };
    }

    public static String labelOf(BillingOperationType type) {
        return switch (type) {
            case CREATE_LATE_PAYMENT_SETTINGS -> "Alta del recargo por mora";
            case ISSUE_INVOICE -> "Emisión de factura";
            case APPROVE_DRAFT_INVOICES -> "Aprobación de borradores de facturas";
            case CHANGE_BILLING_CONFIGURATION -> "Cambio de configuración de facturación";
            case CANCEL_CONFIGURATION_VERSION -> "Cancelación de una versión de configuración";
            case ALLOCATE_ACCOUNT_CREDIT -> "Imputación de saldo a favor";
            case ISSUE_CREDIT_NOTE -> "Nota de crédito";
            case ISSUE_LATE_FEE_DEBIT_NOTE -> "Nota de débito por recargo";
            case ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE -> "Nota de crédito por corrección de recargo";
            case CANCEL_INVOICE -> "Cancelación de factura";
            case CANCEL_INVOICES_AFTER_WITHDRAWAL -> "Cancelación de facturas por baja";
            case REGISTER_PAYMENT -> "Registro de pago";
            case CREATE_DISCOUNT_DEFINITION -> "Alta de promoción";
            case CLOSE_DISCOUNT_DEFINITION -> "Cierre de promoción";
            case GRANT_STUDENT_DISCOUNT -> "Descuento a un alumno";
            case REVOKE_STUDENT_DISCOUNT -> "Revocación de descuento";
            case ACQUIRE_COHORT_PROMOTION -> "Adhesión a promoción de cohorte";
            case REGISTER_GATEWAY_PAYMENT -> "Registro de pago online";
            case APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT -> "Aprobación de comprobante de transferencia";
            case SUBMIT_BANK_TRANSFER_RECEIPT -> "Carga de comprobante de transferencia";
            case APPROVE_PENDING_BANK_TRANSFER_PAYMENT -> "Aprobación de transferencia pendiente";
            case REJECT_PENDING_BANK_TRANSFER_PAYMENT -> "Rechazo de transferencia pendiente";
            case REVOKE_BANK_TRANSFER_APPROVAL -> "Revocación de transferencia aprobada";
            case REGISTER_CHARGEBACK -> "Contracargo";
            case REVERSE_CHARGEBACK -> "Reversión de contracargo";
            case ISSUE_REFUND -> "Reembolso";
            case COMPLETE_REFUND -> "Reembolso realizado";
            case FAIL_REFUND -> "Reembolso fallido";
            case CHANGE_PRICE -> "Cambio de precio";
            case CANCEL_PRICE_VERSION -> "Cancelación de una versión de precio";
            case REQUEST_ANNUAL_UPFRONT -> "Pedido de pago anual";
            case DISCARD_ANNUAL_UPFRONT_REQUEST -> "Descarte de pedido de pago anual";
        };
    }

    public static Set<BillingOperationType> typesOf(Collection<AuthorizationOperationFamily> families) {
        return Arrays.stream(BillingOperationType.values()).filter(type -> families.contains(familyOf(type))).collect(Collectors.toSet());
    }
}
