package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PersonContactFinder {

    private final PersonRepository personRepository;

    public PersonContactFinder(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public Optional<EmailRecipient> findEmailRecipient(Long personId) {
        return personRepository.findById(personId)
                .filter(person -> person.getEmail() != null)
                .map(this::toRecipient);
    }

    private EmailRecipient toRecipient(Person person) {
        return new EmailRecipient(person.getId(), person.getEmail(), Boolean.TRUE.equals(person.getEmailVerified()));
    }
}
