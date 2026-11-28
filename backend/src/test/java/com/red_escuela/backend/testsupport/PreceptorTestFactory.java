package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class PreceptorTestFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private final EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final TransactionTemplate transactions;

    public PreceptorTestFactory(EntityManager entityManager, RoleRepository roleRepository, TransactionTemplate transactions) {
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.transactions = transactions;
    }

    public Long createPreceptorId() {
        return createPersonWithRole("PRECEPTOR");
    }

    public Long createPersonWithRole(String roleName) {
        return transactions.execute(status -> {
            String dni = "7" + SEQUENCE.incrementAndGet();
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            entityManager.persist(person);
            return person.getId();
        });
    }
}
