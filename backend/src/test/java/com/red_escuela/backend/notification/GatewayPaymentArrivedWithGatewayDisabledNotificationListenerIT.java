package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.GatewayPaymentArrivedWithGatewayDisabledEvent;
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
@Tag("D-256")
class GatewayPaymentArrivedWithGatewayDisabledNotificationListenerIT extends AbstractIntegrationTest {

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

    private Person persistSecretariatPerson(String dni) {
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
            person.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
            entityManager.persist(person);
            return person;
        });
    }

    private long notificationsFor(Person person, Long paymentId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.GATEWAY_PAYMENT_WITH_GATEWAY_OFF
                        && paymentId.equals(notification.getRelatedEntityId()))
                .count();
    }

    private long processedHandlersOf(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, eventId);
    }

    private void publish(GatewayPaymentArrivedWithGatewayDisabledEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    @Test
    void aGatewayPaymentWithTheGatewayOffNotifiesTheSecretariatOnceEvenIfTheEventIsRedelivered() {
        Person secretariat = persistSecretariatPerson("9" + System.nanoTime());
        Long paymentId = System.nanoTime();
        GatewayPaymentArrivedWithGatewayDisabledEvent event = GatewayPaymentArrivedWithGatewayDisabledEvent.arrivedFor(paymentId, 77L, "MERCADO_PAGO",
                new BigDecimal("5000.00"), "ARS");

        publish(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(1L);
            assertThat(notificationsFor(secretariat, paymentId)).isEqualTo(1L);
        });
        publish(event);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event.eventId())).isEqualTo(1L);
            assertThat(notificationsFor(secretariat, paymentId)).isEqualTo(1L);
        });
    }
}
