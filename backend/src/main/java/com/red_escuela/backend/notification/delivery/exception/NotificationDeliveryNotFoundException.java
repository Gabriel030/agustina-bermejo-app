package com.red_escuela.backend.notification.delivery.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NotificationDeliveryNotFoundException extends NotFoundException {

    public NotificationDeliveryNotFoundException(Long deliveryId) {
        super("Notification delivery " + deliveryId + " not found");
    }
}
