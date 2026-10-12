package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AuthorizationRequestedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class BillingOperationNotificationListenerIT extends AbstractIntegrationTest {

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

    private Person persistDirector(String dni) {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Director");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
            entityManager.persist(person);
            return person;
        });
    }

    @Test
    void authorizationRequestWithAMaximumLengthReasonStillNotifiesTheDirector() {
        Person director = persistDirector("6" + System.nanoTime());
        Long authorizationRequestId = System.nanoTime();
        AuthorizationRequestedEvent event = AuthorizationRequestedEvent.requestedFor(
                authorizationRequestId, "CREDIT_NOTE", "DIRECTOR", "x".repeat(1000));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Notification> received = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(director.getId()).stream()
                    .filter(notification -> notification.getType() == NotificationType.AUTHORIZATION_REQUESTED
                            && authorizationRequestId.equals(notification.getRelatedEntityId()))
                    .toList();
            assertThat(received).hasSize(1);
            assertThat(received.get(0).getMessage().length()).isLessThanOrEqualTo(Notification.MESSAGE_COLUMN_LENGTH);
        });
    }
}
