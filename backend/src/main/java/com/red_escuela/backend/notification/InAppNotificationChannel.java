package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;


@Component
public class InAppNotificationChannel implements NotificationChannel {

    private static final String ELLIPSIS = "…";

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceFinder preferenceFinder;

    public InAppNotificationChannel(NotificationRepository notificationRepository, NotificationPreferenceFinder preferenceFinder) {
        this.notificationRepository = notificationRepository;
        this.preferenceFinder = preferenceFinder;
    }

    @Override
    public void send(Person recipient, DomainNotificationEvent event) {
        if (!recipientWantsInApp(recipient, event)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(event.type());
        notification.setMessage(abbreviateToColumnLength(event.message()));
        notification.setRelatedEntityType(event.relatedEntityType());
        notification.setRelatedEntityId(event.relatedEntityId());
        notification.setCreatedAt(ApplicationTime.now());
        notificationRepository.save(notification);
    }

    private boolean recipientWantsInApp(Person recipient, DomainNotificationEvent event) {
        return UserPreferenceEventResolver.resolve(event.type())
                .map(preferenceEvent -> preferenceFinder.isInAppEnabled(recipient.getId(), preferenceEvent))
                .orElse(true);
    }

    public static String abbreviateToColumnLength(String message) {
        if (message == null || message.length() <= Notification.MESSAGE_COLUMN_LENGTH) {
            return message;
        }
        int end = Notification.MESSAGE_COLUMN_LENGTH - ELLIPSIS.length();
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + ELLIPSIS;
    }
}
