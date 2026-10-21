package com.red_escuela.backend.billing.payment.domain;

@FunctionalInterface
public interface ReceiptProtectionRule {
    boolean protects(ReceiptProtectionFacts facts);
}
