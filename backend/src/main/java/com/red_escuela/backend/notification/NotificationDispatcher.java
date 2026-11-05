package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.api.PersonRecipientFinder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDispatcher {

    private final PersonRecipientFinder personRecipientFinder;
    private final List<NotificationChannel> channels;

    public NotificationDispatcher(PersonRecipientFinder personRecipientFinder, List<NotificationChannel> channels) {
        this.personRecipientFinder = personRecipientFinder;
        this.channels = channels;
    }

    @EventListener
    public void onDomainNotificationEvent(DomainNotificationEvent event) {
        for (NotificationRecipient recipient : event.recipients()) {
            for (Person person : recipient.resolve(personRecipientFinder)) {
                for (NotificationChannel channel : channels) {
                    channel.send(person, event);
                }
            }
        }
    }
}
