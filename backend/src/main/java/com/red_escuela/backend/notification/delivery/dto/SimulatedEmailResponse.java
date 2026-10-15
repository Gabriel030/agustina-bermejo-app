package com.red_escuela.backend.notification.delivery.dto;

import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;

import java.time.Instant;
import java.time.LocalDate;

public record SimulatedEmailResponse(Long id, String kind, DeliveryStatus status, String statusReason, String maskedRecipient, String subject,
                                     String bodyText, LocalDate scheduledFor, Instant sentAt) {

    public static SimulatedEmailResponse from(NotificationDelivery delivery, String maskedRecipient) {
        return new SimulatedEmailResponse(delivery.getId(), delivery.getKind(), delivery.getStatus(), delivery.getStatusReason(), maskedRecipient,
                delivery.getSubject(), delivery.getBodyText(), delivery.getScheduledFor(), delivery.getSentAt());
    }
}
