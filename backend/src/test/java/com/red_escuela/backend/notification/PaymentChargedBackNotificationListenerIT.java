package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentChargedBackEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
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
import java.time.LocalDate;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-183")
@Tag("D-206")
class PaymentChargedBackNotificationListenerIT extends AbstractIntegrationTest {

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

    private Person persistStaff(String role) {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String dni = role.charAt(0) + "" + System.nanoTime();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            entityManager.persist(person);
            return person;
        });
    }

    private long chargebackNotificationsFor(Person person, Long paymentId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PAYMENT_CHARGED_BACK && paymentId.equals(notification.getRelatedEntityId())).count();
    }

    private PaymentChargedBackEvent publish(PaymentChargedBackEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return event;
    }

    @Test
    void aChargebackNotifiesTheAdministrationOnceEvenIfTheEventIsDeliveredTwice() {
        Person administrative = persistStaff("ADMINISTRATIVO");
        Person director = persistStaff("DIRECTOR");
        Long paymentId = System.nanoTime();
        PaymentChargedBackEvent event = PaymentChargedBackEvent.chargedBackFor(paymentId, 1L, 2L, LocalDate.of(2027, 3, 9), NonNegativeMoney.of(ars(new BigDecimal("1000.00"))), "REF");

        publish(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(chargebackNotificationsFor(administrative, paymentId)).isEqualTo(1L);
            assertThat(chargebackNotificationsFor(director, paymentId)).isEqualTo(1L);
        });
        publish(event);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(chargebackNotificationsFor(administrative, paymentId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1L);
        });
    }
}
