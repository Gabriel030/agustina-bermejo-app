package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
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
class StudentSpecificationsLikeIT extends AbstractIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManager entityManager;

    private Student persist(String name, String dni) {
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
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        entityManager.persist(student);
        return student;
    }

    private List<Long> search(String q) {
        entityManager.flush();
        return studentRepository.findAll(StudentSpecifications.withSearch(q)).stream().map(Student::getId).toList();
    }

    @Test
    void underscoreSearchMatchesOnlyLiteralUnderscore() {
        Student ana = persist("Ana", "93100001");
        Student bruno = persist("Bruno", "93100002");
        Student literal = persist("A_a", "93100003");

        assertThat(search("_")).contains(literal.getId()).doesNotContain(ana.getId(), bruno.getId());
    }

    @Test
    void percentSearchMatchesNobody() {
        Student ana = persist("Ana", "93100004");
        Student bruno = persist("Bruno", "93100005");

        assertThat(search("%")).doesNotContain(ana.getId(), bruno.getId());
    }
}
