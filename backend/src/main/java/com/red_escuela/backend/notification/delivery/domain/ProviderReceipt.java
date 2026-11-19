package com.red_escuela.backend.notification.delivery.domain;

public record ProviderReceipt(String providerMessageId) {

    public static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 120;

    public ProviderReceipt {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalArgumentException("A provider receipt needs a message id");
        }
        if (providerMessageId.length() > MAX_PROVIDER_MESSAGE_ID_LENGTH) {
            providerMessageId = providerMessageId.substring(0, MAX_PROVIDER_MESSAGE_ID_LENGTH);
        }
    }
}
