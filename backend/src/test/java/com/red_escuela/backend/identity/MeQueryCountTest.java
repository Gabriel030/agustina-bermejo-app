package com.red_escuela.backend.identity;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.controller.PersonController;
import com.red_escuela.backend.identity.dto.PersonResponse;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeQueryCountTest extends AbstractIntegrationTest {

    private static final int OWN_PROFILE_QUERIES = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PersonController personController;

    @Autowired
    private RoleRepository roleRepository;

    private int sequence = 72000000;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void meReadStaysConstantWithManyRoles() {
        List<Role> roles = roleRepository.findAll();
        assertThat(roles.size()).isGreaterThan(1);
        Person withOneRole = personWithRoles(roles.subList(0, 1));
        Person withAllRoles = personWithRoles(roles);
        entityManager.flush();

        long statementsWithOneRole = statementsOfOwnProfileRead(withOneRole);
        long statementsWithAllRoles = statementsOfOwnProfileRead(withAllRoles);

        assertThat(statementsWithAllRoles).isEqualTo(statementsWithOneRole);
        assertThat(statementsWithAllRoles).isEqualTo(OWN_PROFILE_QUERIES);
        entityManager.clear();
        PersonResponse response = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, OWN_PROFILE_QUERIES,
                () -> readOwnProfileAs(withAllRoles));
        assertThat(response.roles()).hasSize(roles.size());
    }

    private long statementsOfOwnProfileRead(Person person) {
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            readOwnProfileAs(person);
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private PersonResponse readOwnProfileAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null, "ROLE_ALUMNO"));
        return personController.getOwnProfile();
    }

    private Person personWithRoles(List<Role> roles) {
        Person person = persistPerson();
        person.getRoles().addAll(roles);
        return person;
    }

    private Person persistPerson() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }
}
