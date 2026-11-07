package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.domain.DomainNotificationEvent;
import com.red_escuela.backend.identity.entity.Person;

public interface NotificationChannel {
    void send(Person recipient, DomainNotificationEvent event);
}
