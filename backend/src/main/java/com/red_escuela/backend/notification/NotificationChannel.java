package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.Person;

public interface NotificationChannel {
    void send(Person recipient, DomainNotificationEvent event);
}
