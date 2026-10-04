package com.red_escuela.backend.notification.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.notification.exception.NotOwnNotificationException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EntityManager entityManager;

    private Person persistPerson(String dni) {
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
        entityManager.persist(person);
        return person;
    }

    private Notification persistNotification(Person recipient) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(NotificationType.SECTION_EVAL_CONFIG_APPROVED);
        notification.setMessage("Mensaje de prueba");
        notification.setCreatedAt(LocalDateTime.now());
        entityManager.persist(notification);
        return notification;
    }

    @Test
    void listForPersonReturnsOnlyThatPersonsNotifications() {
        Person destinatario = persistPerson("91000001");
        Person otraPersona = persistPerson("91000002");
        persistNotification(destinatario);
        persistNotification(otraPersona);
        entityManager.flush();

        List<Notification> notifications = notificationService.listForPerson(destinatario.getId());

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getRecipient().getId()).isEqualTo(destinatario.getId());
    }

    @Test
    void markAsReadSetsReadTrue() {
        Person destinatario = persistPerson("91000003");
        Notification notification = persistNotification(destinatario);
        entityManager.flush();

        Notification actualizada = notificationService.markAsRead(notification.getId(), destinatario.getId());

        assertThat(actualizada.isRead()).isTrue();
    }

    @Test
    void markAsReadOnSomeoneElsesNotificationFails() {
        Person destinatario = persistPerson("91000004");
        Person otraPersona = persistPerson("91000005");
        Notification notification = persistNotification(destinatario);
        entityManager.flush();

        assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), otraPersona.getId()))
                .isInstanceOf(NotOwnNotificationException.class);
    }
}
