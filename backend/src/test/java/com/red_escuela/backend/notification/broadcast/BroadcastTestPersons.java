package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;

final class BroadcastTestPersons {

    private BroadcastTestPersons() {
    }

    static Person createStudentPerson(EntityManager entityManager, RoleRepository roleRepository, String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
