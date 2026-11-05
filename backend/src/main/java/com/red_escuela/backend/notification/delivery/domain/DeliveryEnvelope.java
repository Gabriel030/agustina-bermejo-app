package com.red_escuela.backend.notification.delivery.domain;

import java.time.LocalDate;
import java.util.Objects;

public record DeliveryEnvelope(String dedupKey, DeliveryCategory category, String kind, int priority, Long recipientPersonId,
                               String relatedEntityType, Long relatedEntityId, LocalDate scheduledFor) {

    public static final int MAX_DEDUP_KEY_LENGTH = 120;
    public static final int MAX_KIND_LENGTH = 64;
    public static final int MAX_RELATED_ENTITY_TYPE_LENGTH = 60;
    public static final int HIGHEST_PRIORITY = 1;

    public DeliveryEnvelope {
        requireText(dedupKey, MAX_DEDUP_KEY_LENGTH, "dedupKey");
        requireText(kind, MAX_KIND_LENGTH, "kind");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(recipientPersonId, "recipientPersonId");
        Objects.requireNonNull(scheduledFor, "scheduledFor");
        if (priority < HIGHEST_PRIORITY) {
            throw new IllegalArgumentException("A priority is at least " + HIGHEST_PRIORITY);
        }
        if (relatedEntityType != null && (relatedEntityType.isBlank() || relatedEntityType.length() > MAX_RELATED_ENTITY_TYPE_LENGTH
                || relatedEntityType.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("A related entity type is between 1 and " + MAX_RELATED_ENTITY_TYPE_LENGTH + " characters");
        }
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " is between 1 and " + maxLength + " characters");
        }
    }
}
