package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDispatcher {

    private final PersonRepository personRepository;
    private final List<NotificationChannel> channels;

    public NotificationDispatcher(PersonRepository personRepository, List<NotificationChannel> channels) {
        this.personRepository = personRepository;
        this.channels = channels;
    }

    @EventListener
    public void onDomainNotificationEvent(DomainNotificationEvent event) {
        for (NotificationRecipient recipient : event.recipients()) {
            for (Person person : recipient.resolve(personRepository)) {
                for (NotificationChannel channel : channels) {
                    channel.send(person, event);
                }
            }
        }
    }
}
