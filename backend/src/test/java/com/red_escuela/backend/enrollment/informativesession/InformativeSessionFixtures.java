package com.red_escuela.backend.enrollment.informativesession;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicLong;

class InformativeSessionFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong(71_000_000L);

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    InformativeSessionFixtures(TransactionTemplate transactionTemplate, EntityManager entityManager,
                               RoleRepository roleRepository, JwtService jwtService) {
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.jwtService = jwtService;
    }

    String tokenOfNewPersonWithRole(String roleName) {
        Person person = transactionTemplate.execute(status -> {
            long sequence = SEQUENCE.incrementAndGet();
            Address address = new Address();
            entityManager.persist(address);
            Person created = new Person();
            created.setName("Persona");
            created.setLastname("Charla");
            created.setDni(Long.toString(sequence));
            created.setUsername("charla" + sequence);
            created.setEmail("charla" + sequence + "@example.com");
            created.setAddress(address);
            created.setStatus(StatusType.ACTIVE);
            created.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            entityManager.persist(created);
            entityManager.flush();
            created.getRoles().size();
            return created;
        });
        return jwtService.generateAccessToken(person);
    }
}
