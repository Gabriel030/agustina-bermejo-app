package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;

final class AudienceTestPersons {

    private AudienceTestPersons() {
    }

    static Person create(EntityManager entityManager, RoleRepository roleRepository, String username, boolean emailVerified,
                         String phone, String... roles) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setName("Nombre");
        person.setLastname(username);
        person.setEmail(username + "@example.com");
        person.setEmailVerified(emailVerified);
        person.setPhoneNumber(phone);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        for (String role : roles) {
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
        }
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
