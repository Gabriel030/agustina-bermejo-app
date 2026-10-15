package com.red_escuela.backend.shared.mail;

public record EmailDeliveryReceipt(String providerMessageId, boolean simulated) {

    public EmailDeliveryReceipt {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalArgumentException("A delivery receipt needs a provider message id");
        }
    }
}
