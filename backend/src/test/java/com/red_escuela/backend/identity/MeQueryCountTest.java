package com.red_escuela.backend.identity;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.controller.PersonController;
import com.red_escuela.backend.identity.dto.PersonResponse;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeQueryCountTest extends AbstractIntegrationTest {

    private static final int FEW = 1;
    private static final int MANY = 120;
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
    void meReadStaysConstantWithManyEnrollmentsAndRoles() {
        Person withFew = personWithEnrollments(FEW);
        Person withMany = personWithEnrollments(MANY);
        entityManager.flush();
        entityManager.clear();

        long statementsWithFew = statementsOfOwnProfileRead(withFew);
        long statementsWithMany = statementsOfOwnProfileRead(withMany);

        assertThat(statementsWithMany).isEqualTo(statementsWithFew);
        assertThat(statementsWithMany).isLessThanOrEqualTo(OWN_PROFILE_QUERIES);
        PersonResponse response = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, OWN_PROFILE_QUERIES,
                () -> readOwnProfileAs(withMany));
        assertThat(response.roles()).hasSize(roleRepository.findAll().size());
    }

    private long statementsOfOwnProfileRead(Person person) {
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        readOwnProfileAs(person);
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }

    private PersonResponse readOwnProfileAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null, "ROLE_ALUMNO"));
        Supplier<PersonResponse> read = personController::getOwnProfile;
        return read.get();
    }

    private Person personWithEnrollments(int enrollments) {
        Institution institution = new Institution();
        institution.setName("Perf institute");
        entityManager.persist(institution);
        Person person = persistPerson();
        for (Role role : roleRepository.findAll()) {
            person.getRoles().add(role);
        }
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        for (int index = 0; index < enrollments; index++) {
            Program program = new Program();
            program.setName("Program " + person.getDni() + "-" + index);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(enrollment);
        }
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
