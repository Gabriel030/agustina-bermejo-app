package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.repository.PersonRepository;

import java.util.List;

public record PersonRecipient(Person person) implements NotificationRecipient {
    @Override
    public List<Person> resolve(PersonRepository personRepository) {
        return List.of(person);
    }
}
