package com.red_escuela.backend.notification.delivery;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryDispatchJob;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmailDeliveryDispatchJobIT extends AbstractIntegrationTest {
    private static final DeliveryContent EMAIL = DeliveryContent.forEmail("Tu cuota vence mañana", "Texto del aviso", "<p>Texto del aviso</p>");

    @Autowired
    private EmailDeliveryDispatchJob job;
    @Autowired
    private NotificationDeliveryService deliveryService;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson() {
        String dni = "9" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private Long queueMailFor(LocalDate day) {
        Person person = persistPerson();
        String key = "dispatch:" + System.nanoTime();
        DeliveryEnvelope envelope = new DeliveryEnvelope(key, DeliveryCategory.COLLECTION_REMINDER, "BEFORE_DUE_REMINDER", 3, person.getId(), "Invoice", 11L, day);
        deliveryService.enqueue(new DeliveryRequest(envelope, NotificationType.COLLECTION_REMINDER, null, EMAIL, false));
        return deliveryRepository.findByDedupKeyAndChannel(key, DeliveryChannel.EMAIL).orElseThrow().getId();
    }

    private DeliveryStatus statusOf(Long id) {
        return deliveryRepository.findById(id).orElseThrow().getStatus();
    }

    private void at(LocalDate day, int hour, int minute) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(hour, minute)));
    }

    @Test
    @Tag("c06")
    @Tag("D-342")
    void aMailIsNotSentBeforeNineAndIsSentFromNine() {
        LocalDate tuesday = LocalDate.of(2027, 3, 9);
        Long id = queueMailFor(tuesday);

        at(tuesday, 8, 59);
        job.dispatchDueEmails();
        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.QUEUED);

        at(tuesday, 9, 0);
        job.dispatchDueEmails();
        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("c06")
    @Tag("D-342")
    void theWindowIsClosedAtElevenAndStillOpenAtTenFiftyNine() {
        LocalDate tuesday = LocalDate.of(2027, 3, 16);
        Long beforeClosing = queueMailFor(tuesday);
        Long atClosing = queueMailFor(tuesday);

        at(tuesday, 11, 0);
        job.dispatchDueEmails();
        assertThat(statusOf(atClosing)).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(statusOf(beforeClosing)).isEqualTo(DeliveryStatus.QUEUED);

        at(tuesday, 10, 59);
        job.dispatchDueEmails();
        assertThat(statusOf(beforeClosing)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(statusOf(atClosing)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("c07")
    @Tag("D-342")
    @Tag("D-321")
    void nothingIsSentOnSaturdaySundayOrAHoliday() {
        LocalDate saturday = LocalDate.of(2027, 4, 3);
        LocalDate sunday = LocalDate.of(2027, 4, 4);
        LocalDate holiday = LocalDate.of(2027, 4, 7);
        Long onSaturday = queueMailFor(saturday);
        Long onSunday = queueMailFor(sunday);
        Long onHoliday = queueMailFor(holiday);
        jdbc.update("insert into holiday (date, description) values (?, 'Feriado de prueba')", holiday);
        try {
            at(saturday, 10, 0);
            job.dispatchDueEmails();
            at(sunday, 10, 0);
            job.dispatchDueEmails();
            at(holiday, 10, 0);
            job.dispatchDueEmails();

            assertThat(statusOf(onSaturday)).isEqualTo(DeliveryStatus.QUEUED);
            assertThat(statusOf(onSunday)).isEqualTo(DeliveryStatus.QUEUED);
            assertThat(statusOf(onHoliday)).isEqualTo(DeliveryStatus.QUEUED);
        } finally {
            jdbc.update("delete from holiday where date = ?", holiday);
        }

        at(holiday, 10, 0);
        job.dispatchDueEmails();
        assertThat(statusOf(onHoliday)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("c06")
    void aMailScheduledForAnotherDayIsNotSentToday() {
        LocalDate tuesday = LocalDate.of(2027, 4, 13);
        Long tomorrows = queueMailFor(tuesday.plusDays(1));

        at(tuesday, 10, 0);
        job.dispatchDueEmails();

        assertThat(statusOf(tomorrows)).isEqualTo(DeliveryStatus.QUEUED);
    }

    @Test
    @Tag("c06")
    @Tag("D-343")
    void aDeferredMailWaitsForItsNotBeforeInstant() {
        LocalDate tuesday = LocalDate.of(2027, 4, 20);
        Long id = queueMailFor(tuesday);
        Instant tenThirty = LocalDateTime.of(tuesday, LocalTime.of(10, 30)).atZone(MutableClock.INSTANCE.getZone()).toInstant();
        transactionTemplate.executeWithoutResult(status -> deliveryRepository.findById(id).orElseThrow().defer("DAILY_CAP", tenThirty));

        at(tuesday, 10, 29);
        job.dispatchDueEmails();
        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.DEFERRED);

        at(tuesday, 10, 30);
        job.dispatchDueEmails();
        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("c05")
    void theJobDoesNotWaitForADeliveryLockedByAnotherWorker() throws Exception {
        LocalDate tuesday = LocalDate.of(2027, 4, 27);
        Long locked = queueMailFor(tuesday);
        Long free = queueMailFor(tuesday);
        at(tuesday, 10, 0);
        CountDownLatch lockTaken = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                deliveryRepository.findByIdForUpdate(locked).orElseThrow();
                lockTaken.countDown();
                awaitQuietly(releaseLock);
            }));
            assertThat(lockTaken.await(30, TimeUnit.SECONDS)).isTrue();

            Future<?> run = pool.submit(job::dispatchDueEmails);
            run.get(15, TimeUnit.SECONDS);

            assertThat(statusOf(locked)).isEqualTo(DeliveryStatus.QUEUED);
            assertThat(statusOf(free)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
            releaseLock.countDown();
            holder.get(30, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
        }

        job.dispatchDueEmails();
        assertThat(statusOf(locked)).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
