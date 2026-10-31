package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProgramEnrollmentApplicationRejectionIT extends AbstractIntegrationTest {

    @Autowired
    private ProgramEnrollmentApplicationRejection applicationRejection;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long applicationId;
    private Person decidedBy;

    @BeforeEach
    void persistPendingApplication() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("A" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = new Program();
            program.setName("Diseño Gráfico");
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            decidedBy = persistPerson("B" + System.nanoTime());
            ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
            application.setStudent(student);
            application.setProgram(program);
            application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
            application.setRequestedAt(LocalDateTime.now());
            entityManager.persist(application);
            applicationId = application.getId();
        });
    }

    @Test
    void rejectionIsCommittedWithoutAnOuterTransaction() {
        applicationRejection.reject(applicationId, decidedBy, "Documentación ilegible");

        assertThat(rejectionReason()).isEqualTo("Documentación ilegible");
        assertThat(jdbcTemplate.queryForObject("select status from program_enrollment_application where id = ?", String.class, applicationId))
                .isEqualTo("REJECTED");
    }

    @Test
    void rejectingAnAlreadyRejectedApplicationFailsAndKeepsTheFirstDecision() {
        applicationRejection.reject(applicationId, decidedBy, "Primera decisión");

        assertThatThrownBy(() -> applicationRejection.reject(applicationId, decidedBy, "Segunda decisión"))
                .isInstanceOf(InvalidProgramEnrollmentApplicationStateException.class);

        assertThat(rejectionReason()).isEqualTo("Primera decisión");
    }

    private String rejectionReason() {
        return jdbcTemplate.queryForObject("select rejection_reason from program_enrollment_application where id = ?", String.class, applicationId);
    }

    private Person persistPerson(String dni) {
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
        entityManager.persist(person);
        return person;
    }
}
