package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EmailDispatchSource {

    List<DispatchItem> itemsOf(NotificationDelivery delivery);

    Optional<DeliveryContent> contentWithoutItems(NotificationDelivery delivery, Set<String> itemKeys);

    boolean isStillApplicable(NotificationDelivery delivery, LocalDate date);
}
