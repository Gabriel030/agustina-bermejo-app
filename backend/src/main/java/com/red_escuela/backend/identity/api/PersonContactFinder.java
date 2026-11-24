package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public Map<Long, PersonContactSummary> findContactSummariesByPersonIdIn(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personRepository.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> new PersonContactSummary(person.getId(),
                        person.getName() + " " + person.getLastname(), person.getEmail() == null ? null : person.getEmail().value())));
    }

    @Transactional(readOnly = true)
    public Optional<String> findPhoneNumber(Long personId) {
        return personRepository.findById(personId).map(Person::getPhoneNumber).filter(phone -> !phone.isBlank());
    }

    private EmailRecipient toRecipient(Person person) {
        return new EmailRecipient(person.getId(), person.getEmail(), Boolean.TRUE.equals(person.getEmailVerified()));
    }
}
