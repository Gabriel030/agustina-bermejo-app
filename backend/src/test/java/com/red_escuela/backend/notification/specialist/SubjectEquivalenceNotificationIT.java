package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.SlowTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static com.red_escuela.backend.testsupport.AsyncNotifications.awaitAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@SlowTest(reason = "Waits for asynchronous notification listeners after committed subject equivalence events")
class SubjectEquivalenceNotificationIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Person student;
    private Person bystander;
    private Person staff;

    @BeforeEach
    void persistPeople() {
        long suffix = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            student = persistPerson("eqs" + suffix, "ALUMNO");
            bystander = persistPerson("eqb" + suffix, "ALUMNO");
            staff = persistPerson("eqa" + suffix, "ADMINISTRATIVO");
        });
    }

    @Test
    void recordedEquivalenceNotifiesOnlyTheStudentOnce() {
        publishCommitted(SubjectEquivalenceRecordedEvent.recordedFor(21L, 5L, 3L, student.getId(), "Algebra"));

        awaitAsserted(() -> assertThat(of(student, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).singleElement()
                .satisfies(notification -> assertThat(notification.getMessage())
                        .isEqualTo("Te reconocieron la equivalencia de Algebra: ahora figura como aprobada en tu plan de estudio.")));
        assertThat(of(bystander, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).isEmpty();
        assertThat(of(staff, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).isEmpty();
    }

    @Test
    void voidedEquivalenceNotifiesOnlyTheStudentWithoutReason() {
        publishCommitted(SubjectEquivalenceVoidedEvent.voidedFor(22L, 5L, 3L, student.getId(), "Algebra"));

        awaitAsserted(() -> assertThat(of(student, NotificationType.SUBJECT_EQUIVALENCE_VOIDED)).singleElement()
                .satisfies(notification -> assertThat(notification.getMessage())
                        .isEqualTo("Se anuló la equivalencia de Algebra. Consultá con la institución si tenés dudas.")));
        assertThat(of(bystander, NotificationType.SUBJECT_EQUIVALENCE_VOIDED)).isEmpty();
        assertThat(of(staff, NotificationType.SUBJECT_EQUIVALENCE_VOIDED)).isEmpty();
    }

    @Test
    void republishingTheSameRecordedEventNotifiesOnce() {
        SubjectEquivalenceRecordedEvent event = SubjectEquivalenceRecordedEvent.recordedFor(23L, 5L, 3L, student.getId(), "Algebra");

        publishCommitted(event);
        awaitAsserted(() -> assertThat(of(student, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).hasSize(1));
        publishCommitted(event);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(of(student, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).hasSize(1));
    }

    @Test
    void aRolledBackPublicationNeverNotifies() {
        SubjectEquivalenceRecordedEvent event = SubjectEquivalenceRecordedEvent.recordedFor(24L, 5L, 3L, student.getId(), "Algebra");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(of(student, NotificationType.SUBJECT_EQUIVALENCE_RECORDED)).isEmpty());
    }

    private void publishCommitted(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private List<Notification> of(Person person, NotificationType type) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == type).toList();
    }

    private Person persistPerson(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Equiv");
        person.setLastname(username);
        person.setUsername(username);
        person.setDni(username);
        person.setEmail(username + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
