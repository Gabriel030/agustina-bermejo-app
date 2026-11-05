package com.red_escuela.backend.billing.payment.policy;

public record CashPaymentPreviewCheck(String submittedFingerprint, String currentFingerprint) {
}
