package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.api.PersonRecipientFinder;

import java.util.List;

public interface NotificationRecipient {
    List<Person> resolve(PersonRecipientFinder personRecipientFinder);
}
