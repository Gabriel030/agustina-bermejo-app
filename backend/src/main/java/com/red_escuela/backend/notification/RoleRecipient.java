package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;

import java.util.List;

public record RoleRecipient(String role) implements NotificationRecipient {
    @Override
    public List<Person> resolve(PersonRepository personRepository) {
        return personRepository.findByRolesName(role);
    }
}
