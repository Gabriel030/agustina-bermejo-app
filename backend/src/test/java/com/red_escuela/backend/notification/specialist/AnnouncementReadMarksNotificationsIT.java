package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.announcement.events.AnnouncementReadEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AsyncNotifications;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementReadMarksNotificationsIT extends AbstractIntegrationTest {
    private static final AtomicLong ANNOUNCEMENT_IDS = new AtomicLong(950_000L);

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson() {
        String dni = "7" + System.nanoTime();
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Alumno");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private Long insertNotification(Person recipient, String type, String entityType, Long entityId) {
        return jdbc.queryForObject(
                "insert into notification (recipient_id, type, message, read, related_entity_type, related_entity_id, created_at) values (?, ?, 'm', false, ?, ?, now()) returning id",
                Long.class, recipient.getId(), type, entityType, entityId);
    }

    private boolean isRead(Long notificationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select read from notification where id = ?", Boolean.class, notificationId));
    }

    private void publishRead(Long announcementId, Person reader) {
        transactions.executeWithoutResult(status -> eventPublisher.publishEvent(AnnouncementReadEvent.readFor(announcementId, reader.getId())));
    }

    @Test
    void readingAnAnnouncementMarksOnlyTheReadersAnnouncementNotificationsAsRead() {
        Person reader = persistPerson();
        Person otherStudent = persistPerson();
        Long announcementId = ANNOUNCEMENT_IDS.incrementAndGet();
        Long otherAnnouncementId = ANNOUNCEMENT_IDS.incrementAndGet();
        Long published = insertNotification(reader, "ANNOUNCEMENT_PUBLISHED", "Announcement", announcementId);
        Long updated = insertNotification(reader, "ANNOUNCEMENT_UPDATED", "Announcement", announcementId);
        Long otherAnnouncement = insertNotification(reader, "ANNOUNCEMENT_PUBLISHED", "Announcement", otherAnnouncementId);
        Long otherEntityType = insertNotification(reader, "ANNOUNCEMENT_PUBLISHED", "Section", announcementId);
        Long otherStudentPublished = insertNotification(otherStudent, "ANNOUNCEMENT_PUBLISHED", "Announcement", announcementId);
        Long otherStudentUpdated = insertNotification(otherStudent, "ANNOUNCEMENT_UPDATED", "Announcement", announcementId);

        publishRead(announcementId, reader);

        AsyncNotifications.awaitAsserted(() -> assertThat(isRead(published) && isRead(updated)).isTrue());
        assertThat(isRead(otherAnnouncement)).isFalse();
        assertThat(isRead(otherEntityType)).isFalse();
        assertThat(isRead(otherStudentPublished)).isFalse();
        assertThat(isRead(otherStudentUpdated)).isFalse();
    }

    @Test
    void readingTheSameAnnouncementTwiceIsIdempotent() {
        Person reader = persistPerson();
        Long announcementId = ANNOUNCEMENT_IDS.incrementAndGet();
        Long published = insertNotification(reader, "ANNOUNCEMENT_PUBLISHED", "Announcement", announcementId);

        publishRead(announcementId, reader);
        AsyncNotifications.awaitAsserted(() -> assertThat(isRead(published)).isTrue());
        publishRead(announcementId, reader);
        AsyncNotifications.awaitPublicationsOfDrained(jdbc, "%AnnouncementReadEvent");

        assertThat(isRead(published)).isTrue();
    }
}
