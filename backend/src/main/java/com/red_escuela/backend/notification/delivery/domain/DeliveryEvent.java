package com.red_escuela.backend.notification.delivery.domain;

public enum DeliveryEvent {
    QUEUE_FOR_DISPATCH,
    DELIVER_IN_APP,
    SUPPRESS,
    SEND_VIA_PROVIDER,
    SEND_VIA_SIMULATOR,
    DEFER,
    DISCARD,
    GIVE_UP
}
