package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PendingActivationPersonFinder {

    private final PersonRepository personRepository;

    public PendingActivationPersonFinder(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<PendingActivationPerson> findStudentsPendingActivationByIdIn(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return personRepository.findStudentsPendingActivationByIdIn(personIds).stream()
                .map(this::toPendingActivationPerson)
                .toList();
    }

    private PendingActivationPerson toPendingActivationPerson(Person person) {
        String email = person.getEmail() == null ? "" : person.getEmail().value();
        return new PendingActivationPerson(person.getId(), email, person.getName() + " " + person.getLastname());
    }
}
