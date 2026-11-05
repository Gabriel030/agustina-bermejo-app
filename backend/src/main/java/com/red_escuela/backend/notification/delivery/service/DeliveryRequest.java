package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.Objects;

public record DeliveryRequest(DeliveryEnvelope envelope, NotificationType inAppType, DeliveryContent inApp, DeliveryContent email,
                              boolean emailPrecedesDueDate) {

    public DeliveryRequest {
        Objects.requireNonNull(envelope, "envelope");
        if (inApp == null && email == null) {
            throw new IllegalArgumentException("A delivery request needs at least one channel");
        }
        if (inApp != null && inAppType == null) {
            throw new IllegalArgumentException("An in-app delivery needs a notification type");
        }
        if (inApp != null && inApp.subject() != null) {
            throw new IllegalArgumentException("An in-app delivery has no subject");
        }
        if (email != null && email.subject() == null) {
            throw new IllegalArgumentException("An email delivery needs a subject");
        }
    }

    public boolean sendsInApp() {
        return inApp != null;
    }

    public boolean sendsEmail() {
        return email != null;
    }
}
