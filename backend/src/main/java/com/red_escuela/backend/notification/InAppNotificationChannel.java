package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class InAppNotificationChannel implements NotificationChannel {

    private final NotificationRepository notificationRepository;

    public InAppNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(Person recipient, DomainNotificationEvent event) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(event.type());
        notification.setMessage(event.message());
        notification.setRelatedEntityType(event.relatedEntityType());
        notification.setRelatedEntityId(event.relatedEntityId());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}
