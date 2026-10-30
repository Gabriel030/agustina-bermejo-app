package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.billing.events.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.billing.events.SuspensionLiftedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.LongFunction;

import static com.red_escuela.backend.testsupport.AsyncNotifications.awaitAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-183")
class DomainEventNotificationListenersAsyncIT extends AbstractIntegrationTest {

    private static final Instant FIRST_OCCURRENCE = Instant.parse("2027-03-01T10:00:00Z");
    private static final Instant SECOND_OCCURRENCE = Instant.parse("2027-03-02T10:00:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IncompleteEventPublications incompleteEventPublications;

    private Person persistPerson() {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String dni = "N" + System.nanoTime();
            person.setName("Persona");
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

    private long notificationsOf(Long personId, NotificationType type) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId).stream().filter(notification -> notification.getType() == type).count();
    }

    private String eventPattern(Long personId) {
        return ":" + personId + "[,}]";
    }

    private long incompletePublicationsOf(Long personId) {
        return jdbc.queryForObject("select count(*) from event_publication where completion_date is null and serialized_event ~ ?", Long.class, eventPattern(personId));
    }

    private void publish(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private Long publishTogetherWithABusinessChange(DomainEvent event) {
        return transactionTemplate.execute(status -> {
            Address businessChange = new Address();
            entityManager.persist(businessChange);
            eventPublisher.publishEvent(event);
            return businessChange.getId();
        });
    }

    private void assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(LongFunction<DomainEvent> eventFor, NotificationType type) {
        Person notYetCommitted = persistPerson();
        Long missingPersonId = notYetCommitted.getId() + 1_000_000L;

        DomainEvent event = eventFor.apply(missingPersonId);

        Long businessChangeId = publishTogetherWithABusinessChange(event);

        await().atMost(Duration.ofSeconds(30)).until(() -> incompletePublicationsOf(missingPersonId) == 1L);
        assertThat(jdbc.queryForObject("select count(*) from address where id = ?", Long.class, businessChangeId)).isEqualTo(1L);
        assertThat(notificationsOf(missingPersonId, type)).isZero();

        jdbc.update("update person set id = ? where id = ?", missingPersonId, notYetCommitted.getId());
        incompleteEventPublications.resubmitIncompletePublications(publication -> event.equals(publication.getEvent()));

        awaitAsserted(() -> {
            assertThat(notificationsOf(missingPersonId, type)).isEqualTo(1L);
            assertThat(incompletePublicationsOf(missingPersonId)).isZero();
        });
    }

    private void assertTwoOccurrencesNotifyTwiceAndARedeliveryOfOneNotifiesOnce(LongFunction<DomainEvent> firstOccurrence, LongFunction<DomainEvent> secondOccurrence,
                                                                               NotificationType type) {
        Person person = persistPerson();
        DomainEvent first = firstOccurrence.apply(person.getId());

        publish(first);
        publish(secondOccurrence.apply(person.getId()));

        awaitAsserted(() -> assertThat(notificationsOf(person.getId(), type)).isEqualTo(2L));
        publish(first);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationsOf(person.getId(), type)).isEqualTo(2L);
            assertThat(incompletePublicationsOf(person.getId())).isZero();
        });
    }

    @Test
    void aFailureNotifyingAGraduationDecisionDoesNotRollBackTheOperationAndIsRedelivered() {
        assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(
                personId -> new GraduationRequestApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, personId, "Carrera"),
                NotificationType.GRADUATION_REQUEST_APPROVED);
    }

    @Test
    void aFailureNotifyingAnAttendanceChangeDecisionDoesNotRollBackTheOperationAndIsRedelivered() {
        assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(
                personId -> new AttendanceChangeRequestApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, personId, "Alumno Prueba",
                        LocalDate.of(2027, 3, 1), "Ausente", "Presente"),
                NotificationType.ATTENDANCE_CHANGE_REQUEST_APPROVED);
    }

    @Test
    void aFailureNotifyingASectionEvaluationConfigDecisionDoesNotRollBackTheOperationAndIsRedelivered() {
        assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(
                personId -> new SectionEvaluationConfigApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, "Materia", "Comisión A", personId),
                NotificationType.SECTION_EVAL_CONFIG_APPROVED);
    }

    @Test
    void aFailureNotifyingAnEnrollmentEventDoesNotRollBackTheOperationAndIsRedelivered() {
        assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(
                personId -> new EnrollmentFeeExemptionGrantedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, personId),
                NotificationType.ENROLLMENT_FEE_EXEMPTION_GRANTED);
    }

    @Test
    void aFailureNotifyingABillingEventDoesNotRollBackTheOperationAndIsRedelivered() {
        assertAFailingListenerDoesNotRollBackThePublisherAndTheRegistryRedeliversIt(
                personId -> new PaymentReceiptApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 1L, YearMonth.of(2027, 3), personId),
                NotificationType.PAYMENT_RECEIPT_APPROVED);
    }

    @Test
    void aRolledBackPublisherNeverCreatesTheNotification() {
        Person person = persistPerson();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new GraduationRequestApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 2L, person.getId(), "Carrera"));
            status.setRollbackOnly();
        });

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationsOf(person.getId(), NotificationType.GRADUATION_REQUEST_APPROVED)).isZero();
            assertThat(incompletePublicationsOf(person.getId())).isZero();
        });
    }

    @Test
    void twoReminderOccurrencesWithTheSameValuesNotifyTwiceAndARedeliveryOfOneNotifiesOnce() {
        assertTwoOccurrencesNotifyTwiceAndARedeliveryOfOneNotifiesOnce(
                personId -> new SectionEvaluationConfigReminderEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 3L, "Materia", "Comisión A", personId, 5),
                personId -> new SectionEvaluationConfigReminderEvent(CorrelationIds.current(), SECOND_OCCURRENCE, 3L, "Materia", "Comisión A", personId, 5),
                NotificationType.SECTION_EVAL_CONFIG_REMINDER);
    }

    @Test
    void twoSuspensionLiftsWithTheSameValuesNotifyTwiceAndARedeliveryOfOneNotifiesOnce() {
        assertTwoOccurrencesNotifyTwiceAndARedeliveryOfOneNotifiesOnce(
                personId -> new SuspensionLiftedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, SuspensionLiftedEvent.EnrollmentType.COURSE, 4L, personId,
                        YearMonth.of(2027, 1), YearMonth.of(2027, 3)),
                personId -> new SuspensionLiftedEvent(CorrelationIds.current(), SECOND_OCCURRENCE, SuspensionLiftedEvent.EnrollmentType.COURSE, 4L, personId,
                        YearMonth.of(2027, 1), YearMonth.of(2027, 3)),
                NotificationType.SUSPENSION_LIFTED);
    }

    @Test
    void twoReceiptApprovalsWithTheSameValuesNotifyTwiceAndARedeliveryOfOneNotifiesOnce() {
        assertTwoOccurrencesNotifyTwiceAndARedeliveryOfOneNotifiesOnce(
                personId -> new PaymentReceiptApprovedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 5L, YearMonth.of(2027, 3), personId),
                personId -> new PaymentReceiptApprovedEvent(CorrelationIds.current(), SECOND_OCCURRENCE, 5L, YearMonth.of(2027, 3), personId),
                NotificationType.PAYMENT_RECEIPT_APPROVED);
    }

    @Test
    void twoFeeExemptionGrantsWithTheSameValuesNotifyTwiceAndARedeliveryOfOneNotifiesOnce() {
        assertTwoOccurrencesNotifyTwiceAndARedeliveryOfOneNotifiesOnce(
                personId -> new EnrollmentFeeExemptionGrantedEvent(CorrelationIds.current(), FIRST_OCCURRENCE, 6L, personId),
                personId -> new EnrollmentFeeExemptionGrantedEvent(CorrelationIds.current(), SECOND_OCCURRENCE, 6L, personId),
                NotificationType.ENROLLMENT_FEE_EXEMPTION_GRANTED);
    }
}
