package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;

import java.util.List;

public record PersonIdRecipient(Long personId) implements NotificationRecipient {
    @Override
    public List<Person> resolve(PersonRepository personRepository) {
        return personRepository.findById(personId).map(List::of).orElse(List.of());
    }
}
