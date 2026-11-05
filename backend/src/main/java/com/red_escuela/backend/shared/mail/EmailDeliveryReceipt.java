package com.red_escuela.backend.shared.mail;

public record EmailDeliveryReceipt(String providerMessageId, boolean simulated) {

    public static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 120;

    public EmailDeliveryReceipt {
        if (providerMessageId == null || providerMessageId.isBlank() || providerMessageId.length() > MAX_PROVIDER_MESSAGE_ID_LENGTH) {
            throw new IllegalArgumentException("A delivery receipt needs a provider message id of at most " + MAX_PROVIDER_MESSAGE_ID_LENGTH + " characters");
        }
    }
}
