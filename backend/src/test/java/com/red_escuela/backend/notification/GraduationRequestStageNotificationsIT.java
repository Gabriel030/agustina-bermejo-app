package com.red_escuela.backend.notification;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static com.red_escuela.backend.testsupport.AsyncNotifications.awaitAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class GraduationRequestStageNotificationsIT extends AbstractIntegrationTest {

    private static final Instant FIRST_OCCURRENCE = Instant.parse("2027-03-01T10:00:00Z");
    private static final Instant SECOND_OCCURRENCE = Instant.parse("2027-03-02T10:00:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson(String roleName) {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String dni = "G" + System.nanoTime();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            entityManager.flush();
            if (roleName != null) {
                jdbc.update("insert into person_role (person_id, role_id) select ?, id from role where name = ?", person.getId(), roleName);
            }
            return person;
        });
    }

    private long notificationsOf(Person person, NotificationType type) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream().filter(notification -> notification.getType() == type).count();
    }

    private void publish(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private void assertStaysAt(Person person, NotificationType type, long expected) {
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(notificationsOf(person, type)).isEqualTo(expected));
    }

    @Test
    void aRequestedGraduationNotifiesTheStudentAndTheStaffOnceEach() {
        Person student = persistPerson(null);
        Person administrative = persistPerson("ADMINISTRATIVO");
        Person director = persistPerson("DIRECTOR");
        Person teacher = persistPerson("DOCENTE");
        GraduationRequestedEvent event = new GraduationRequestedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, student.getId(), "Ana Perez", "Carrera");

        publish(event);

        awaitAsserted(() -> {
            assertThat(notificationsOf(student, NotificationType.GRADUATION_REQUEST_CREATED)).isEqualTo(1L);
            assertThat(notificationsOf(administrative, NotificationType.GRADUATION_REQUEST_PENDING_REVIEW)).isEqualTo(1L);
            assertThat(notificationsOf(director, NotificationType.GRADUATION_REQUEST_PENDING_REVIEW)).isEqualTo(1L);
        });
        assertThat(notificationsOf(student, NotificationType.GRADUATION_REQUEST_PENDING_REVIEW)).isZero();
        assertThat(notificationsOf(teacher, NotificationType.GRADUATION_REQUEST_PENDING_REVIEW)).isZero();
        publish(event);
        assertStaysAt(student, NotificationType.GRADUATION_REQUEST_CREATED, 1L);
        assertStaysAt(administrative, NotificationType.GRADUATION_REQUEST_PENDING_REVIEW, 1L);
    }

    @Test
    void aSecondRequestOfTheSameStudentNotifiesAgain() {
        Person student = persistPerson(null);

        publish(new GraduationRequestedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, student.getId(), "Ana Perez", "Carrera"));
        publish(new GraduationRequestedEvent(CorrelationIds.current(), SECOND_OCCURRENCE, 1L, student.getId(), "Ana Perez", "Carrera"));

        awaitAsserted(() -> assertThat(notificationsOf(student, NotificationType.GRADUATION_REQUEST_CREATED)).isEqualTo(2L));
    }

    @Test
    void aVerifiedGraduationNotifiesTheStudentOnce() {
        Person student = persistPerson(null);
        GraduationRequestVerifiedEvent event = new GraduationRequestVerifiedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, student.getId(), "Carrera");

        publish(event);

        awaitAsserted(() -> assertThat(notificationsOf(student, NotificationType.GRADUATION_REQUEST_VERIFIED)).isEqualTo(1L));
        publish(event);
        assertStaysAt(student, NotificationType.GRADUATION_REQUEST_VERIFIED, 1L);
    }
}
