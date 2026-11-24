package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.notification.exception.NotificationNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationInboxManagerTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationInboxManager notificationService;

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
        notification.setCreatedAt(LocalDateTime.now(MutableClock.INSTANCE));
        entityManager.persist(notification);
        return notification;
    }

    private Notification persistNotificationAt(Person recipient, LocalDateTime createdAt, boolean read) {
        Notification notification = persistNotification(recipient);
        notification.setCreatedAt(createdAt);
        notification.setRead(read);
        return notification;
    }

    @Test
    void pageForPersonReturnsOnlyThatPersonsNotifications() {
        Person destinatario = persistPerson("91000001");
        Person otraPersona = persistPerson("91000002");
        Long notificationOfDestinatarioId = persistNotification(destinatario).getId();
        persistNotification(otraPersona);
        entityManager.flush();

        PageResponse<NotificationResponse> page = notificationService.pageForPerson(destinatario.getId(), 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).id()).isEqualTo(notificationOfDestinatarioId);
    }

    @Test
    void pageForPersonOrdersNewestFirstAndSlicesInSql() {
        Person destinatario = persistPerson("91000004");
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 10, 0);
        Notification oldest = persistNotificationAt(destinatario, base, false);
        Notification middle = persistNotificationAt(destinatario, base.plusHours(1), false);
        Notification newest = persistNotificationAt(destinatario, base.plusHours(2), false);
        entityManager.flush();

        PageResponse<NotificationResponse> first = notificationService.pageForPerson(destinatario.getId(), 0, 2);
        PageResponse<NotificationResponse> second = notificationService.pageForPerson(destinatario.getId(), 1, 2);

        assertThat(first.items()).extracting(NotificationResponse::id).containsExactly(newest.getId(), middle.getId());
        assertThat(second.items()).extracting(NotificationResponse::id).containsExactly(oldest.getId());
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.totalElements()).isEqualTo(3);
    }

    @Test
    void pageForPersonBoundsSizeToMaximum() {
        Person destinatario = persistPerson("91000005");
        persistNotification(destinatario);
        entityManager.flush();

        PageResponse<NotificationResponse> page = notificationService.pageForPerson(destinatario.getId(), -3, 100000);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PageResponse.MAX_SIZE);
    }

    @Test
    void countUnreadForPersonCountsOnlyUnreadOfThatPerson() {
        Person destinatario = persistPerson("91000006");
        Person otraPersona = persistPerson("91000007");
        LocalDateTime now = LocalDateTime.now(MutableClock.INSTANCE);
        persistNotificationAt(destinatario, now, false);
        persistNotificationAt(destinatario, now, false);
        persistNotificationAt(destinatario, now, true);
        persistNotificationAt(otraPersona, now, false);
        entityManager.flush();

        assertThat(notificationService.countUnreadForPerson(destinatario.getId())).isEqualTo(2);
    }

    @Test
    void markAsReadSetsReadTrue() {
        Person destinatario = persistPerson("91000003");
        Notification notification = persistNotification(destinatario);
        entityManager.flush();

        NotificationResponse actualizada = notificationService.markAsRead(notification.getId(), destinatario.getId());

        assertThat(actualizada.read()).isTrue();
    }

    @Test
    void markingAnotherUsersNotificationReturnsSameNotFoundAsMissing() {
        Person destinatario = persistPerson("91000004");
        Person otraPersona = persistPerson("91000005");
        Notification notification = persistNotification(destinatario);
        entityManager.flush();

        assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), otraPersona.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markingMissingNotificationFailsWithTheSameException() {
        Person destinatario = persistPerson("91000006");
        entityManager.flush();

        assertThatThrownBy(() -> notificationService.markAsRead(987654321L, destinatario.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
