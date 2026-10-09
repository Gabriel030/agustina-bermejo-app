package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent;
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
@Tag("D-134")
@Tag("D-144")
class PaymentLeftAsAccountCreditNotificationListenerIT extends AbstractIntegrationTest {

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

    private long notificationsFor(Person person, Long paymentId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PAYMENT_LEFT_AS_ACCOUNT_CREDIT && paymentId.equals(notification.getRelatedEntityId()))
                .count();
    }

    private long processedHandlersOf(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, eventId);
    }

    private PaymentLeftAsAccountCreditEvent publish(PaymentLeftAsAccountCreditEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return event;
    }

    @Test
    void aPaymentThatLeftAsAccountCreditNotifiesTheStudentAndTheSecretariatOnce() {
        Person student = persistPerson("7" + System.nanoTime(), null);
        Person secretariat = persistPerson("8" + System.nanoTime(), "PRECEPTOR");
        Long paymentId = System.nanoTime();
        PaymentLeftAsAccountCreditEvent event = publish(PaymentLeftAsAccountCreditEvent.leftFor(paymentId, 55L, student.getId(), new BigDecimal("5000.00"), "ARS"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L);
            assertThat(notificationsFor(student, paymentId)).isEqualTo(1L);
            assertThat(notificationsFor(secretariat, paymentId)).isEqualTo(1L);
        });
    }

    @Test
    void aRedeliveredEventDoesNotNotifyAgain() {
        Person student = persistPerson("6" + System.nanoTime(), null);
        Long paymentId = System.nanoTime();
        PaymentLeftAsAccountCreditEvent event = publish(PaymentLeftAsAccountCreditEvent.leftFor(paymentId, 56L, student.getId(), new BigDecimal("5000.00"), "ARS"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L));

        publish(event);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(2L);
            assertThat(notificationsFor(student, paymentId)).isEqualTo(1L);
        });
    }
}
