package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.repository.PersonRepository;

import java.util.List;

public interface NotificationRecipient {
    List<Person> resolve(PersonRepository personRepository);
}
