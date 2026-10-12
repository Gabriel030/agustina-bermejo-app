package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.LateFeeIssuedEvent;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-244")
@Tag("D-117")
class LateFeeNotificationListenerIT extends AbstractIntegrationTest {

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

    private long notificationsFor(Person person, Long debitNoteId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.LATE_FEE_ISSUED && debitNoteId.equals(notification.getRelatedEntityId()))
                .count();
    }

    private List<String> messagesFor(Person person, Long debitNoteId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.LATE_FEE_ISSUED && debitNoteId.equals(notification.getRelatedEntityId()))
                .map(notification -> notification.getMessage()).toList();
    }

    private long processedHandlersOf(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, eventId);
    }

    private LateFeeIssuedEvent publish(LateFeeIssuedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return event;
    }

    @Test
    @Tag("D-244")
    @Tag("D-117")
    void anIssuedLateFeeNotifiesTheStudentWithItsPeriodOnceAndARedeliveredEventDoesNotNotifyAgain() {
        Person student = persistPerson("5" + System.nanoTime(), null);
        Long debitNoteId = System.nanoTime();
        LateFeeIssuedEvent event = publish(LateFeeIssuedEvent.issuedFor(debitNoteId, "ND-00000009", 70L, "F-00000070", student.getId(),
                java.time.LocalDate.of(2027, 3, 12), java.time.LocalDate.of(2027, 3, 31), 20, com.red_escuela.backend.billing.events.LateFeeCutoffKind.MONTH_CLOSE,
                java.time.LocalDate.of(2027, 4, 1), com.red_escuela.backend.shared.domain.Money.of(new BigDecimal("4000.00"), com.red_escuela.backend.shared.domain.Currency.ARS)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(1L);
            assertThat(notificationsFor(student, debitNoteId)).isEqualTo(1L);
            assertThat(messagesFor(student, debitNoteId)).singleElement().asString().contains("del 12/03 al 31/03 (20 días)");
        });

        publish(event);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(1L);
            assertThat(notificationsFor(student, debitNoteId)).isEqualTo(1L);
        });
    }
}
