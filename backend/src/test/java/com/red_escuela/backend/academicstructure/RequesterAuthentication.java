package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class RequesterAuthentication {

    private RequesterAuthentication() {
    }

    public static Person authenticateNewRequester(EntityManager entityManager) {
        var address = new Address();
        entityManager.persist(address);
        var person = new Person();
        var key = "mod" + System.nanoTime();
        person.setName("Solicitante");
        person.setLastname(key);
        person.setDni(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(person.getId()), null, List.of()));
        return person;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
