package com.red_escuela.backend.notification.delivery.domain;

public record DispatchDiscard(long deliveryId, String reason) {

    public DispatchDiscard {
        DeliveryStatusReason.require(reason);
    }
}
