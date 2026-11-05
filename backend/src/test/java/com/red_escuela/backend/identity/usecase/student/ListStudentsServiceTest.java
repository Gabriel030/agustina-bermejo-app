package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
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
class ListStudentsServiceTest extends AbstractIntegrationTest {
    @Autowired
    private ListStudentsUseCase listStudentsUseCase;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Student persistStudent(String dni, String name, String lastname, boolean withStudentRole) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(lastname);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        if (withStudentRole) {
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        }
        entityManager.persist(person);

        Institution institution = new Institution();
        institution.setName("Instituto " + dni);
        entityManager.persist(institution);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        entityManager.refresh(person);
        return student;
    }

    private List<Long> search(String text) {
        return listStudentsUseCase.list(new ListStudentsCommand(StatusType.ACTIVE, null, text)).stream()
                .map(Student::getId)
                .toList();
    }

    @Test
    void confirmedRegistrantWithoutStudentRoleIsNotListedAsStudent() {
        Student enrolled = persistStudent("43000001", "Ana", "Inscripta", true);
        Student registrantOnly = persistStudent("43000002", "Beto", "Aspirante", false);

        List<Long> listed = search(null);

        assertThat(listed).contains(enrolled.getId()).doesNotContain(registrantOnly.getId());
    }

    @Test
    void searchByFullNameMatchesNameAndLastnameInAnyOrderIgnoringAccents() {
        Student lucia = persistStudent("43000003", "Lucía", "Fernández", true);
        Student other = persistStudent("43000004", "Lucía", "Gómez", true);

        assertThat(search("Lucía Fernández")).contains(lucia.getId()).doesNotContain(other.getId());
        assertThat(search("fernandez lucia")).contains(lucia.getId()).doesNotContain(other.getId());
    }

    @Test
    void searchByFileNumberFindsTheStudent() {
        Student student = persistStudent("43000005", "Carla", "Legajo", true);
        Student other = persistStudent("43000006", "Dario", "OtroLegajo", true);

        assertThat(search(student.getPerson().getFile())).contains(student.getId()).doesNotContain(other.getId());
    }
}
