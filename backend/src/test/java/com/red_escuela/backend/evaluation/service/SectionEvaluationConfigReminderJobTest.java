package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SectionEvaluationConfigReminderJobTest extends AbstractIntegrationTest {

    @Autowired
    private SectionEvaluationConfigReminderJob reminderJob;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long personId;
    private Long configId;
    private Long sectionId;
    private Long termId;
    private Long subjectId;
    private Long instructorId;
    private Long addressId;

    private TransactionTemplate inTransaction() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void removeCommittedData() {
        inTransaction().executeWithoutResult(status -> {
            entityManager.createQuery("delete from Notification n where n.relatedEntityType = 'SectionEvaluationConfig' and n.relatedEntityId = :id")
                    .setParameter("id", configId).executeUpdate();
            entityManager.createQuery("delete from SubjectSectionInstructor s where s.section.id = :id")
                    .setParameter("id", sectionId).executeUpdate();
            entityManager.remove(entityManager.find(SectionEvaluationConfig.class, configId));
            entityManager.remove(entityManager.find(SubjectSection.class, sectionId));
            entityManager.remove(entityManager.find(Term.class, termId));
            entityManager.remove(entityManager.find(Subject.class, subjectId));
            entityManager.remove(entityManager.find(Instructor.class, instructorId));
            entityManager.remove(entityManager.find(Person.class, personId));
            entityManager.remove(entityManager.find(Address.class, addressId));
        });
    }

    @Test
    void reminderTenDaysBeforeEnrollmentOpensIsActuallyStoredForTheLeadInstructor() {
        inTransaction().executeWithoutResult(status -> {
            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("MateriaRecordatorio");
            entityManager.persist(subject);
            Term term = new Term();
            term.setYear(2099);
            term.setDescription("Ciclo recordatorio");
            term.setStatus(StatusType.ACTIVE);
            term.setEnrollmentStart(LocalDate.now().plusDays(10).atStartOfDay());
            term.setStartDate(LocalDateTime.now().plusDays(30));
            term.setEndDate(LocalDateTime.now().plusMonths(5));
            entityManager.persist(term);
            entityManager.flush();
            SubjectSection section = subjectSectionService.create(subject.getId(),
                    new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Docente");
            person.setLastname("Recordatorio");
            person.setDni("93000001");
            person.setUsername("93000001");
            person.setEmail("93000001@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            entityManager.persist(instructor);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
            entityManager.flush();
            subjectId = subject.getId();
            termId = term.getId();
            sectionId = section.getId();
            configId = entityManager.createQuery("select c.id from SectionEvaluationConfig c where c.subjectSection.id = :id", Long.class)
                    .setParameter("id", section.getId()).getSingleResult();
            addressId = address.getId();
            personId = person.getId();
            instructorId = instructor.getId();
        });

        reminderJob.sendReminders();

        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId);
        assertThat(notifications).extracting(Notification::getType).containsExactly(NotificationType.SECTION_EVAL_CONFIG_REMINDER);
    }
}
