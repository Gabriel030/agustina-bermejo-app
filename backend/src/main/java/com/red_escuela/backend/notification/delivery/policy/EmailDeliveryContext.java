package com.red_escuela.backend.notification.delivery.policy;

public record EmailDeliveryContext(boolean precedesDueDate, boolean preDueEmailEnabled, boolean emailVerified) {
}
