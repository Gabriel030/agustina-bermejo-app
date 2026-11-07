package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.AnnouncementEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryDispatchJob;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.AsyncNotifications;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementEmailFlowIT extends AbstractIntegrationTest {
    private static final LocalDate SATURDAY = LocalDate.of(2027, 3, 13);
    private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 9);
    private static final AtomicLong ANNOUNCEMENT_IDS = new AtomicLong(900_000L);

    @Autowired
    private AnnouncementEmailListener listener;
    @Autowired
    private AnnouncementEmailDispatchJob announcementJob;
    @Autowired
    private EmailDeliveryDispatchJob collectionJob;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void institutionWithTheSwitchOff() {
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            jdbc.update("update institution set announcement_email_enabled = false");
        });
    }

    private Person persistPerson(boolean verified) {
        String dni = "8" + System.nanoTime();
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Alumno");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(verified);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private AnnouncementPublishedEvent event(Long announcementId, boolean urgent, Person... recipients) {
        return new AnnouncementPublishedEvent(CorrelationIds.current(), Instant.parse("2027-03-13T22:00:00Z"), announcementId, "Algebra",
                java.util.Arrays.stream(recipients).map(Person::getId).toList(), urgent, "Suspension de clases");
    }

    private NotificationDelivery deliveryOf(Long announcementId, Person person) {
        return deliveryRepository.findByDedupKeyAndChannel("announcement:" + announcementId + ":" + person.getId() + ":PUBLISHED", DeliveryChannel.EMAIL)
                .orElseThrow();
    }

    private void handle(AnnouncementPublishedEvent event) {
        listener.emailAnnouncementPublished(event);
        var eventId = DerivedEventIdentity.of(event).eventId();
        AsyncNotifications.awaitAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from processed_effect where event_id = ? and handler_name = ?", Integer.class, eventId,
                AnnouncementEmailListener.PUBLISHED_HANDLER)).isEqualTo(1));
    }

    private void at(LocalDate day, int hour) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(hour, 0)));
    }

    @Test
    void anUrgentAnnouncementIsEmailedOnASaturdayNightAndTheEventIsNotDuplicated() {
        Person verified = persistPerson(true);
        Person unverified = persistPerson(false);
        Long announcementId = ANNOUNCEMENT_IDS.incrementAndGet();
        at(SATURDAY, 22);
        AnnouncementPublishedEvent event = event(announcementId, true, verified, unverified);

        handle(event);
        handle(event);
        announcementJob.dispatchPendingAnnouncementEmails();

        assertThat(deliveryOf(announcementId, verified).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(announcementId, unverified).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where related_entity_type = 'Announcement' and related_entity_id = ?",
                Integer.class, announcementId)).isEqualTo(2);
    }

    @Test
    void theCollectionJobNeverPicksUpAnnouncementDeliveries() {
        Person person = persistPerson(true);
        Long announcementId = ANNOUNCEMENT_IDS.incrementAndGet();
        at(TUESDAY, 10);
        handle(event(announcementId, true, person));

        collectionJob.dispatchDueEmails();

        assertThat(deliveryOf(announcementId, person).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        announcementJob.dispatchPendingAnnouncementEmails();
        assertThat(deliveryOf(announcementId, person).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    void aNonUrgentAnnouncementIsEmailedOnlyWhenTheInstituteTurnsTheSwitchOnAndThePersonKeepsTheEmailPreference() {
        Person optedIn = persistPerson(true);
        Person optedOut = persistPerson(true);
        jdbc.update("insert into notification_preference (person_id, event, channel, enabled) values (?, 'INSTITUTIONAL_NOTICE', 'EMAIL', false)", optedOut.getId());
        Long switchedOff = ANNOUNCEMENT_IDS.incrementAndGet();
        Long switchedOn = ANNOUNCEMENT_IDS.incrementAndGet();
        at(SATURDAY, 22);

        handle(event(switchedOff, false, optedIn, optedOut));
        jdbc.update("update institution set announcement_email_enabled = true");
        handle(event(switchedOn, false, optedIn, optedOut));

        assertThat(deliveryRepository.findByDedupKeyAndChannel("announcement:" + switchedOff + ":" + optedIn.getId() + ":PUBLISHED", DeliveryChannel.EMAIL)).isEmpty();
        assertThat(deliveryOf(switchedOn, optedIn).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(deliveryRepository.findByDedupKeyAndChannel("announcement:" + switchedOn + ":" + optedOut.getId() + ":PUBLISHED", DeliveryChannel.EMAIL)).isEmpty();
    }
}
