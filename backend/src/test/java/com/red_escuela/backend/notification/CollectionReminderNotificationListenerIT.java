package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
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
import java.util.Map;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.ownNotice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CollectionReminderNotificationListenerIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);
    private static final LocalDate OVERDUE_DUE_DATE = LocalDate.of(2027, 3, 5);
    private static final LocalDate UPCOMING_DUE_DATE = LocalDate.of(2027, 3, 15);

    @Autowired
    private CollectionReminderNotificationListener listener;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson(boolean emailVerified) {
        String dni = "8" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(emailVerified);
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

    private static PlannedReminderLine previousLine() {
        return line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
    }

    private static PlannedReminderLine overdueLine() {
        return line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
    }

    private static PlannedReminderMessage mixedDigest() {
        return digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(overdueLine(), previousLine()));
    }

    private static PlannedReminderMessage onlyPreviousDigest() {
        return digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(previousLine()));
    }

    private static PlannedReminderMessage exemptOwnNotice() {
        PlannedReminderLine exempt = line(3, OVERDUE_DUE_DATE, ars(50000), false, PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER);
        return ownNotice(PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER, false, arsBlock(exempt));
    }

    private static CollectionReminderPlannedEvent eventFor(Person person, long reminderId, List<PlannedReminderMessage> inApp, List<PlannedReminderMessage> email) {
        return CollectionReminderPlannedEvent.plannedFor(reminderId, person.getId(), TODAY, inApp, email);
    }

    private List<Map<String, Object>> deliveriesOf(Person person, String channel) {
        return jdbc.queryForList("select * from notification_delivery where recipient_person_id = ? and channel = ? order by dedup_key", person.getId(), channel);
    }

    private long inAppNotificationsOf(Person person) {
        return jdbc.queryForObject("select count(*) from notification where recipient_id = ? and type = 'COLLECTION_REMINDER'", Long.class, person.getId());
    }

    private void deliver(CollectionReminderPlannedEvent event) {
        listener.deliverReminder(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1L));
    }

    @Test
    @Tag("c04")
    @Tag("D-341")
    void onlyTheAccountHolderReceivesTheReminder() {
        Person holder = persistPerson(true);
        Person bystander = persistPerson(true);

        deliver(eventFor(holder, 10L, List.of(mixedDigest()), List.of(mixedDigest())));

        assertThat(deliveriesOf(holder, "IN_APP")).hasSize(1);
        assertThat(deliveriesOf(holder, "EMAIL")).hasSize(1);
        assertThat(inAppNotificationsOf(holder)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where dedup_key like ? and recipient_person_id <> ?", Long.class,
                "collection-reminder:" + holder.getId() + ":%", holder.getId())).isZero();
        assertThat(deliveriesOf(bystander, "IN_APP")).isEmpty();
        assertThat(deliveriesOf(bystander, "EMAIL")).isEmpty();
        assertThat(inAppNotificationsOf(bystander)).isZero();
    }

    @Test
    @Tag("c05")
    @Tag("D-353")
    void sameEventTwiceCreatesOneNotificationAndOneEmail() {
        Person student = persistPerson(true);
        CollectionReminderPlannedEvent event = eventFor(student, 11L, List.of(mixedDigest()), List.of(mixedDigest()));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(inAppNotificationsOf(student)).isEqualTo(1L);
            assertThat(deliveriesOf(student, "EMAIL")).hasSize(1);
        });
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1L);
            assertThat(inAppNotificationsOf(student)).isEqualTo(1L);
            assertThat(deliveriesOf(student, "IN_APP")).hasSize(1);
            assertThat(deliveriesOf(student, "EMAIL")).hasSize(1);
        });
    }

    @Test
    @Tag("D-403")
    @Tag("D-410")
    void theDigestAndTheExemptNoticeOfTheSameDayBothGoOutAsMails() {
        Person student = persistPerson(true);

        deliver(eventFor(student, 12L, List.of(mixedDigest()), List.of(mixedDigest(), exemptOwnNotice())));

        List<Map<String, Object>> mails = deliveriesOf(student, "EMAIL");
        assertThat(mails).hasSize(2);
        assertThat(mails).extracting(mail -> mail.get("dedup_key")).doesNotHaveDuplicates()
                .containsExactly("collection-reminder:" + student.getId() + ":2027-03-09:0", "collection-reminder:" + student.getId() + ":2027-03-09:1");
        assertThat(mails).extracting(mail -> mail.get("kind")).containsExactly("DAILY_DIGEST:AFTER_DUE_REMINDER", "OWN_NOTICE:BEFORE_SUSPENSION_REMINDER");
        assertThat(mails).extracting(mail -> mail.get("priority")).containsExactly(4, 5);
        assertThat(mails).extracting(mail -> mail.get("status")).containsOnly("QUEUED");
        assertThat(mails).extracting(mail -> mail.get("subject")).containsExactly("Tenés una cuota vencida", "Tenés cuotas impagas en tu inscripción");
    }

    @Test
    @Tag("D-353")
    void everyMessageCarriesTheDayTheReminderAndItsRelatedEntity() {
        Person student = persistPerson(true);

        deliver(eventFor(student, 13L, List.of(mixedDigest()), List.of(mixedDigest())));

        for (String channel : List.of("IN_APP", "EMAIL")) {
            Map<String, Object> delivery = deliveriesOf(student, channel).get(0);
            assertThat(delivery.get("scheduled_for").toString()).isEqualTo("2027-03-09");
            assertThat(delivery.get("related_entity_type")).isEqualTo("CollectionReminder");
            assertThat(delivery.get("related_entity_id")).isEqualTo(13L);
            assertThat(delivery.get("category")).isEqualTo("COLLECTION_REMINDER");
        }
    }

    @Test
    @Tag("D-412")
    @Tag("D-340")
    @Tag("D-372")
    void optedOutPreDueItemsLeaveTheMailWithoutThemAndTheInAppStillListsThem() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);

        deliver(eventFor(student, 14L, List.of(mixedDigest()), List.of(mixedDigest())));

        Map<String, Object> mail = deliveriesOf(student, "EMAIL").get(0);
        assertThat(mail.get("status")).isEqualTo("QUEUED");
        assertThat(mail.get("body_text").toString()).contains("venció el 05/03/2027", "ARS 100.000,00").doesNotContain("15/03/2027", "ARS 70.000,00");
        assertThat(mail.get("subject")).isEqualTo("Tenés una cuota vencida");
        Map<String, Object> inApp = deliveriesOf(student, "IN_APP").get(0);
        assertThat(inApp.get("body_text").toString()).contains("Cuota mensual (vence el 15/03/2027, ARS 70.000,00)", "Cuota mensual (venció el 05/03/2027, ARS 100.000,00)");
    }

    @Test
    @Tag("D-412")
    @Tag("D-340")
    @Tag("D-372")
    void aMailWhoseItemsAreAllPreDueIsSuppressedAsOptedOutWhenTheStudentTurnedThemOff() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);

        deliver(eventFor(student, 15L, List.of(onlyPreviousDigest()), List.of(onlyPreviousDigest())));

        Map<String, Object> mail = deliveriesOf(student, "EMAIL").get(0);
        assertThat(mail.get("status")).isEqualTo("SUPPRESSED");
        assertThat(mail.get("status_reason")).isEqualTo("OPTED_OUT");
        assertThat(deliveriesOf(student, "IN_APP")).hasSize(1);
    }

    @Test
    @Tag("D-412")
    @Tag("D-340")
    void aStudentWhoKeepsPreDueMailsReceivesTheFullDigest() {
        Person student = persistPerson(true);

        deliver(eventFor(student, 16L, List.of(mixedDigest()), List.of(mixedDigest())));

        Map<String, Object> mail = deliveriesOf(student, "EMAIL").get(0);
        assertThat(mail.get("status")).isEqualTo("QUEUED");
        assertThat(mail.get("body_text").toString()).contains("venció el 05/03/2027", "vence el 15/03/2027");
    }

    @Test
    @Tag("D-412")
    @Tag("D-410")
    void theExemptNoticeStillGoesOutWhenTheStudentTurnedOffPreDueMailsAndTheDigestIsEmptied() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);

        deliver(eventFor(student, 17L, List.of(onlyPreviousDigest()), List.of(onlyPreviousDigest(), exemptOwnNotice())));

        List<Map<String, Object>> mails = deliveriesOf(student, "EMAIL");
        assertThat(mails).extracting(mail -> mail.get("status")).containsExactly("SUPPRESSED", "QUEUED");
        assertThat(mails.get(0).get("status_reason")).isEqualTo("OPTED_OUT");
    }

    @Test
    @Tag("D-354")
    void aStudentWithoutAVerifiedMailGetsTheInAppAndASuppressedMail() {
        Person student = persistPerson(false);

        deliver(eventFor(student, 18L, List.of(mixedDigest()), List.of(mixedDigest())));

        assertThat(deliveriesOf(student, "IN_APP")).hasSize(1);
        Map<String, Object> mail = deliveriesOf(student, "EMAIL").get(0);
        assertThat(mail.get("status")).isEqualTo("SUPPRESSED");
        assertThat(mail.get("status_reason")).isEqualTo("NO_VERIFIED_EMAIL");
    }

    @Test
    @Tag("D-403")
    void aReminderWithOnlyInAppMessagesQueuesNoMail() {
        Person student = persistPerson(true);

        deliver(eventFor(student, 19L, List.of(mixedDigest()), List.of()));

        assertThat(deliveriesOf(student, "IN_APP")).hasSize(1);
        assertThat(deliveriesOf(student, "EMAIL")).isEmpty();
    }
}
