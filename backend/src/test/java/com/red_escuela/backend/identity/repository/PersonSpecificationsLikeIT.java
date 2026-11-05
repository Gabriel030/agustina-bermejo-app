package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonSpecificationsLikeIT extends AbstractIntegrationTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    private Person persist(String name, String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname("Zzlike");
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private List<Long> search(String q) {
        entityManager.flush();
        return personRepository.findAll(PersonSpecifications.withSearch(q)).stream().map(Person::getId).toList();
    }

    @Test
    void underscoreSearchMatchesOnlyLiteralUnderscore() {
        Person ana = persist("Ana", "93000001");
        Person bruno = persist("Bruno", "93000002");
        Person literal = persist("A_a", "93000003");

        assertThat(search("_")).contains(literal.getId()).doesNotContain(ana.getId(), bruno.getId());
    }

    @Test
    void percentSearchMatchesNobody() {
        Person ana = persist("Ana", "93000004");
        Person bruno = persist("Bruno", "93000005");

        assertThat(search("%")).doesNotContain(ana.getId(), bruno.getId());
    }
}
