package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class LegacyReceiptHasNoVerifiablePaymentException extends PaymentReviewRejectedException {

    public LegacyReceiptHasNoVerifiablePaymentException() {
        super("A legacy receipt without a payment cannot be approved with verified data",
                List.of(new PolicyViolation("LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT", "Este comprobante es anterior al nuevo circuito y no admite monto, fecha ni nota verificados.")));
    }
}
