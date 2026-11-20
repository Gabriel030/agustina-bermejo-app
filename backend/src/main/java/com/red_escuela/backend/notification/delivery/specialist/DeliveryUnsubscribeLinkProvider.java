package com.red_escuela.backend.notification.delivery.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;

import java.util.Optional;

public interface DeliveryUnsubscribeLinkProvider {

    Optional<String> unsubscribeUrlFor(DeliveryCategory category, String kind, Long recipientPersonId);
}
