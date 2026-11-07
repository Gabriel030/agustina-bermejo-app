package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AnnualUpfrontDeadlineNoticePlannedEvent;
import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchPlanningService;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("c05")
class CollectionNoticeInstituteSwitchIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);
    private static final String SWITCH = "BILLING_NOTIFICATIONS";

    @Autowired
    private CollectionReminderNotificationListener reminderListener;
    @Autowired
    private PostWithdrawalSummaryNotificationListener summaryListener;
    @Autowired
    private AnnualUpfrontDeadlineNoticeNotificationListener annualListener;
    @Autowired
    private EmailDispatchPlanningService planningService;
    @Autowired
    private InstituteSettingsSnapshotHolder instituteSnapshotHolder;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restoreTheSwitch() {
        switchInstituteBillingNotifications(true);
    }

    private void switchInstituteBillingNotifications(boolean enabled) {
        jdbc.update("update institute_setting set enabled = ? where code = ?", enabled, SWITCH);
        instituteSnapshotHolder.refresh();
    }

    private Person persistPerson() {
        String dni = "7" + System.nanoTime();
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

    private void turnOffPreDueMails(Person person) {
        transactionTemplate.executeWithoutResult(status -> {
            NotificationPreference preference = new NotificationPreference();
            preference.setPerson(entityManager.getReference(Person.class, person.getId()));
            preference.setEvent(NotificationEvent.PAYMENT_DUE_SOON);
            preference.setChannel(NotificationChannel.EMAIL);
            preference.setEnabled(false);
            entityManager.persist(preference);
        });
    }

    private static PlannedReminderMessage overdueDigest() {
        PlannedReminderLine overdue = line(2, LocalDate.of(2027, 3, 5), ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        return digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(overdue));
    }

    private static PostWithdrawalSummaryPlannedEvent summaryFor(Person person) {
        PostWithdrawalSummaryPlannedEvent.Line line = new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 10),
                ars(100000));
        return PostWithdrawalSummaryPlannedEvent.plannedFor(person.getId(), TODAY, List.of(new PostWithdrawalSummaryPlannedEvent.Block(List.of(line), ars(100000))));
    }

    private static AnnualUpfrontDeadlineNoticePlannedEvent annualFor(Person person, long requestId) {
        return AnnualUpfrontDeadlineNoticePlannedEvent.plannedFor(requestId, 500L + requestId, person.getId(), LocalDateTime.of(2027, 3, 10, 12, 0), ars(900000), TODAY);
    }

    private <E extends DeduplicableDomainEvent> void deliver(E event, Consumer<E> handler) {
        handler.accept(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1L));
    }

    private long deliveriesOf(Person person) {
        return jdbc.queryForObject("select count(*) from notification_delivery where recipient_person_id = ?", Long.class, person.getId());
    }

    private long deliveriesOf(Person person, String channel) {
        return jdbc.queryForObject("select count(*) from notification_delivery where recipient_person_id = ? and channel = ?", Long.class, person.getId(), channel);
    }

    private long inAppNotificationsOf(Person person) {
        return jdbc.queryForObject("select count(*) from notification where recipient_id = ? and type = 'COLLECTION_REMINDER'", Long.class, person.getId());
    }

    @Test
    void reminderIsDeliveredWhenTheInstituteSwitchIsOnWhateverTheUserPreference() {
        for (boolean userPreDueMails : new boolean[]{true, false}) {
            Person student = persistPerson();
            if (!userPreDueMails) {
                turnOffPreDueMails(student);
            }

            deliver(CollectionReminderPlannedEvent.plannedFor(21L, student.getId(), TODAY, List.of(overdueDigest()), List.of(overdueDigest())), reminderListener::deliverReminder);

            assertThat(deliveriesOf(student, "IN_APP")).as("userPreDueMails=%s", userPreDueMails).isEqualTo(1L);
            assertThat(deliveriesOf(student, "EMAIL")).as("userPreDueMails=%s", userPreDueMails).isEqualTo(1L);
            assertThat(inAppNotificationsOf(student)).isEqualTo(1L);
        }
    }

    @Test
    void reminderIsDiscardedWhenTheInstituteSwitchIsOffWhateverTheUserPreference() {
        switchInstituteBillingNotifications(false);
        for (boolean userPreDueMails : new boolean[]{true, false}) {
            Person student = persistPerson();
            if (!userPreDueMails) {
                turnOffPreDueMails(student);
            }

            deliver(CollectionReminderPlannedEvent.plannedFor(22L, student.getId(), TODAY, List.of(overdueDigest()), List.of(overdueDigest())), reminderListener::deliverReminder);

            assertThat(deliveriesOf(student)).as("userPreDueMails=%s", userPreDueMails).isZero();
            assertThat(inAppNotificationsOf(student)).isZero();
        }
    }

    @Test
    void postWithdrawalSummaryFollowsTheInstituteSwitch() {
        Person whileOn = persistPerson();
        deliver(summaryFor(whileOn), summaryListener::deliverSummary);
        switchInstituteBillingNotifications(false);
        Person whileOff = persistPerson();

        deliver(summaryFor(whileOff), summaryListener::deliverSummary);

        assertThat(deliveriesOf(whileOn)).isEqualTo(2L);
        assertThat(deliveriesOf(whileOff)).isZero();
        assertThat(inAppNotificationsOf(whileOff)).isZero();
    }

    @Test
    void annualUpfrontDeadlineNoticeFollowsTheInstituteSwitch() {
        Person whileOn = persistPerson();
        deliver(annualFor(whileOn, 31L), annualListener::deliverNotice);
        switchInstituteBillingNotifications(false);
        Person whileOff = persistPerson();

        deliver(annualFor(whileOff, 32L), annualListener::deliverNotice);

        assertThat(deliveriesOf(whileOn)).isEqualTo(2L);
        assertThat(deliveriesOf(whileOff)).isZero();
        assertThat(inAppNotificationsOf(whileOff)).isZero();
    }

    @Test
    void aQueuedMailIsSuppressedBeforeSendingWhenTheInstituteSwitchIsTurnedOffAfterwards() {
        Person student = persistPerson();
        deliver(annualFor(student, 33L), annualListener::deliverNotice);
        Long mailId = jdbc.queryForObject("select id from notification_delivery where recipient_person_id = ? and channel = 'EMAIL'", Long.class, student.getId());
        switchInstituteBillingNotifications(false);

        boolean suppressed = planningService.suppressBeforeSending(mailId, TODAY);

        Map<String, Object> mail = jdbc.queryForMap("select status, status_reason from notification_delivery where id = ?", mailId);
        assertThat(suppressed).isTrue();
        assertThat(mail.get("status")).isEqualTo("SUPPRESSED");
        assertThat(mail.get("status_reason")).isEqualTo("BILLING_NOTIFICATIONS_OFF");
    }

    @Test
    void aQueuedMailIsKeptWhenTheInstituteSwitchStaysOn() {
        Person student = persistPerson();
        deliver(annualFor(student, 34L), annualListener::deliverNotice);
        Long mailId = jdbc.queryForObject("select id from notification_delivery where recipient_person_id = ? and channel = 'EMAIL'", Long.class, student.getId());

        boolean suppressed = planningService.suppressBeforeSending(mailId, TODAY);

        assertThat(suppressed).isFalse();
        assertThat(jdbc.queryForObject("select status from notification_delivery where id = ?", String.class, mailId)).isEqualTo("QUEUED");
    }
}
