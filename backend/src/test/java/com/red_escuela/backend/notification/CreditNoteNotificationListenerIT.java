package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-184")
@Tag("D-165")
class CreditNoteNotificationListenerIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson(String dni, String role) {
        return transactionTemplate.execute(status -> {
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
            if (role != null) {
                person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            }
            entityManager.persist(person);
            return person;
        });
    }

    private long notificationsFor(Person person, Long creditNoteId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.CREDIT_NOTE_ISSUED && creditNoteId.equals(notification.getRelatedEntityId()))
                .count();
    }

    private long processedHandlersOf(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, eventId);
    }

    private CreditNoteIssuedEvent publish(CreditNoteIssuedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return event;
    }

    @Test
    void anIssuedCreditNoteNotifiesTheStudentAndTheRequesterOnce() {
        Person student = persistPerson("7" + System.nanoTime(), null);
        Person secretariat = persistPerson("8" + System.nanoTime(), "ADMINISTRATIVO");
        Long creditNoteId = System.nanoTime();
        CreditNoteIssuedEvent event = publish(CreditNoteIssuedEvent.issuedFor(creditNoteId, "NC-00000001", 55L, "F-00000055", student.getId(), secretariat.getId(), "BILLING_ERROR", new BigDecimal("5000.00"), "ARS", java.time.LocalDate.of(2027, 3, 20)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L);
            assertThat(notificationsFor(student, creditNoteId)).isEqualTo(1L);
            assertThat(notificationsFor(secretariat, creditNoteId)).isEqualTo(1L);
        });
    }

    @Test
    void aRedeliveredEventDoesNotNotifyAgain() {
        Person student = persistPerson("6" + System.nanoTime(), null);
        Long creditNoteId = System.nanoTime();
        CreditNoteIssuedEvent event = publish(CreditNoteIssuedEvent.issuedFor(creditNoteId, "NC-00000002", 56L, "F-00000056", student.getId(), student.getId(), "BILLING_ERROR", new BigDecimal("5000.00"), "ARS", java.time.LocalDate.of(2027, 3, 20)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L));

        publish(event);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L);
            assertThat(notificationsFor(student, creditNoteId)).isEqualTo(2L);
        });
    }
}
