package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.OverdueContactListUpdatedEvent;
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

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-339")
class OverdueContactListNotificationListenerIT extends AbstractIntegrationTest {

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
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            entityManager.persist(person);
            return person;
        });
    }

    private List<String> contactListMessagesOf(Person person) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.OVERDUE_CONTACT_LIST_UPDATED)
                .map(notification -> notification.getMessage()).toList();
    }

    private void publish(OverdueContactListUpdatedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private long processedHandlersOf(OverdueContactListUpdatedEvent event) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId());
    }

    @Test
    @Tag("D-339")
    void secretariatGetsOneNotificationWithTheCountAndTheNewcomersAndARerunOfTheDayDoesNotRepeatIt() {
        Person administrative = persistPerson("7" + System.nanoTime(), "ADMINISTRATIVO");
        Person preceptor = persistPerson("8" + System.nanoTime(), "PRECEPTOR");
        Person director = persistPerson("9" + System.nanoTime(), "DIRECTOR");
        LocalDate day = LocalDate.of(2027, 3, 25);
        OverdueContactListUpdatedEvent event = OverdueContactListUpdatedEvent.updatedOn(day, 12, 3);

        publish(event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event)).isEqualTo(1L);
            assertThat(contactListMessagesOf(administrative)).containsExactly("12 alumnos para contactar, 3 nuevos");
            assertThat(contactListMessagesOf(preceptor)).containsExactly("12 alumnos para contactar, 3 nuevos");
        });
        assertThat(contactListMessagesOf(director)).isEmpty();

        publish(OverdueContactListUpdatedEvent.updatedOn(day, 14, 5));

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedHandlersOf(event)).isEqualTo(1L);
            assertThat(contactListMessagesOf(administrative)).hasSize(1);
        });
    }

    @Test
    @Tag("D-339")
    void theMessageUsesTheSingularAndOmitsTheNewcomersWhenThereAreNone() {
        assertThat(OverdueContactListNotificationListener.messageOf(1, 1)).isEqualTo("1 alumno para contactar, 1 nuevo");
        assertThat(OverdueContactListNotificationListener.messageOf(1, 0)).isEqualTo("1 alumno para contactar");
        assertThat(OverdueContactListNotificationListener.messageOf(5, 0)).isEqualTo("5 alumnos para contactar");
        assertThat(OverdueContactListNotificationListener.messageOf(12, 3)).isEqualTo("12 alumnos para contactar, 3 nuevos");
    }
}
