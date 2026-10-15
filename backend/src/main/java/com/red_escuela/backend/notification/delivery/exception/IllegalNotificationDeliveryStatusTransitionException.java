package com.red_escuela.backend.notification.delivery.exception;

import com.red_escuela.backend.notification.delivery.domain.DeliveryEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalNotificationDeliveryStatusTransitionException extends ConflictException {

    public IllegalNotificationDeliveryStatusTransitionException(DeliveryStatus from, DeliveryEvent event) {
        super("Notification delivery in status " + from + " does not accept " + event);
    }
}
