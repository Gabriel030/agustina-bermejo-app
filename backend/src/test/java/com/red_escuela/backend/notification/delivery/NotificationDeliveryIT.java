package com.red_escuela.backend.notification.delivery;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.EmailRecipientLookup;
import com.red_escuela.backend.identity.api.NotificationPreferenceQuery;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.mail.MockTransactionalEmailGateway;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailDeliveryException;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDeliveryIT extends AbstractIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 3, 9);
    private static final DeliveryContent IN_APP = DeliveryContent.forInApp("Vence mañana tu cuota de marzo");
    private static final DeliveryContent EMAIL = DeliveryContent.forEmail("Tu cuota vence mañana", "Texto del aviso", "<p>Texto del aviso</p>");

    @Autowired
    private NotificationDeliveryService deliveryService;
    @Autowired
    private EmailDeliveryChannel emailChannel;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private TransactionalEmailGateway gateway;
    @Autowired
    private EmailRecipientLookup emailRecipientLookup;
    @Autowired
    private NotificationPreferenceQuery preferenceQuery;
    @Autowired
    private PolicySet<EmailDeliveryContext> emailDeliveryPolicySet;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson(boolean emailVerified) {
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

    private DeliveryRequest request(Person person, String dedupKey, boolean precedesDueDate) {
        return new DeliveryRequest(envelope(person, dedupKey, "BEFORE_DUE_REMINDER"), NotificationType.COLLECTION_REMINDER, IN_APP, EMAIL, precedesDueDate);
    }

    private DeliveryEnvelope envelope(Person person, String dedupKey, String kind) {
        return new DeliveryEnvelope(dedupKey, DeliveryCategory.COLLECTION_REMINDER, kind, 3, person.getId(), "Invoice", 11L, DAY);
    }

    private String uniqueKey() {
        return "reminder:" + System.nanoTime();
    }

    private NotificationDelivery deliveryOf(String dedupKey, DeliveryChannel channel) {
        return deliveryRepository.findByDedupKeyAndChannel(dedupKey, channel).orElseThrow();
    }

    private long countNotifications(String dedupKey) {
        return jdbc.queryForObject("select count(*) from notification where dedup_key = ?", Long.class, dedupKey);
    }

    private long countDeliveries(String dedupKey) {
        return jdbc.queryForObject("select count(*) from notification_delivery where dedup_key = ?", Long.class, dedupKey);
    }

    @Test
    @Tag("c01")
    @Tag("D-343")
    void theDefaultGatewayIsTheSimulatedOne() {
        assertThat(gateway).isInstanceOf(MockTransactionalEmailGateway.class);
    }

    @Test
    @Tag("c01")
    @Tag("D-343")
    void aQueuedMailGoesOutThroughTheSimulatedGatewayAndKeepsItsBody() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        deliveryService.enqueue(request(student, key, false));
        Long id = deliveryOf(key, DeliveryChannel.EMAIL).getId();

        emailChannel.dispatchDelivery(id);

        NotificationDelivery sent = deliveryRepository.findById(id).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(sent.getProviderMessageId()).startsWith("mock-");
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getSubject()).isEqualTo("Tu cuota vence mañana");
        assertThat(sent.getBodyText()).isEqualTo("Texto del aviso");
        assertThat(sent.getBodyHtml()).isEqualTo("<p>Texto del aviso</p>");
        assertThat(sent.getStatusReason()).isNull();
    }

    @Test
    @Tag("c02")
    @Tag("D-340")
    void aStudentWhoTurnedOffPreDueMailsGetsTheInAppAndASuppressedMail() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);
        String key = uniqueKey();

        deliveryService.enqueue(request(student, key, true));

        assertThat(deliveryOf(key, DeliveryChannel.IN_APP).getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(countNotifications(key)).isEqualTo(1);
        NotificationDelivery mail = deliveryOf(key, DeliveryChannel.EMAIL);
        assertThat(mail.getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(mail.getStatusReason()).isEqualTo("OPTED_OUT");
    }

    @Test
    @Tag("c02")
    @Tag("D-340")
    void aStudentWhoTurnedOffPreDueMailsStillGetsTheMailAfterTheDueDate() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);
        String key = uniqueKey();

        deliveryService.enqueue(request(student, key, false));

        assertThat(deliveryOf(key, DeliveryChannel.EMAIL).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    }

    @Test
    @Tag("c02")
    @Tag("D-372")
    void whetherAMailIsPreDueIsJudgedByItsOwnRequestAndNotByItsKind() {
        Person student = persistPerson(true);
        turnOffPreDueMails(student);
        String lateRepeat = uniqueKey();
        String earlySend = uniqueKey();

        deliveryService.enqueue(request(student, lateRepeat, false));
        deliveryService.enqueue(request(student, earlySend, true));

        assertThat(deliveryOf(lateRepeat, DeliveryChannel.EMAIL).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(deliveryOf(earlySend, DeliveryChannel.EMAIL).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
    }

    @Test
    @Tag("c02")
    @Tag("D-340")
    void aStudentWithoutStoredPreferenceReceivesPreDueMails() {
        Person student = persistPerson(true);
        String key = uniqueKey();

        deliveryService.enqueue(request(student, key, true));

        assertThat(deliveryOf(key, DeliveryChannel.EMAIL).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(preferenceQuery.isPreDueReminderEmailEnabled(student.getId())).isTrue();
    }

    @Test
    @Tag("c03")
    @Tag("D-354")
    void anUnverifiedAddressSuppressesTheMailButNotTheInApp() {
        Person student = persistPerson(false);
        String key = uniqueKey();

        deliveryService.enqueue(request(student, key, false));

        NotificationDelivery mail = deliveryOf(key, DeliveryChannel.EMAIL);
        assertThat(mail.getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(mail.getStatusReason()).isEqualTo("NO_VERIFIED_EMAIL");
        assertThat(deliveryOf(key, DeliveryChannel.IN_APP).getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(countNotifications(key)).isEqualTo(1);
    }

    @Test
    @Tag("c03")
    @Tag("D-354")
    void aPersonWhoseVerificationWasNeverSetCountsAsUnverified() {
        Person student = persistPerson(true);
        jdbc.update("update person set email_verified = null where id = ?", student.getId());
        String key = uniqueKey();

        deliveryService.enqueue(request(student, key, false));

        assertThat(deliveryOf(key, DeliveryChannel.EMAIL).getStatusReason()).isEqualTo("NO_VERIFIED_EMAIL");
        assertThat(emailRecipientLookup.findEmailRecipient(student.getId())).hasValueSatisfying(recipient -> assertThat(recipient.verified()).isFalse());
    }

    @Test
    @Tag("c03")
    @Tag("D-354")
    void anAddressThatLostItsVerificationBeforeTheSendIsSuppressedInsteadOfSent() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        deliveryService.enqueue(request(student, key, false));
        jdbc.update("update person set email_verified = false where id = ?", student.getId());
        Long id = deliveryOf(key, DeliveryChannel.EMAIL).getId();

        emailChannel.dispatchDelivery(id);

        NotificationDelivery mail = deliveryRepository.findById(id).orElseThrow();
        assertThat(mail.getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(mail.getStatusReason()).isEqualTo("NO_VERIFIED_EMAIL");
        assertThat(mail.getProviderMessageId()).isNull();
    }

    @Test
    @Tag("c05")
    void theSameRequestEnqueuedTwiceLeavesOneNotificationAndOneMail() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        DeliveryRequest request = request(student, key, false);

        deliveryService.enqueue(request);
        deliveryService.enqueue(request);

        assertThat(countNotifications(key)).isEqualTo(1);
        assertThat(countDeliveries(key)).isEqualTo(2);
        assertThat(deliveryOf(key, DeliveryChannel.EMAIL).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    }

    @Test
    @Tag("c05")
    void aSecondDispatchOfAnAlreadySentMailDoesNotSendItAgain() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        deliveryService.enqueue(request(student, key, false));
        Long id = deliveryOf(key, DeliveryChannel.EMAIL).getId();
        emailChannel.dispatchDelivery(id);
        String firstMessageId = deliveryRepository.findById(id).orElseThrow().getProviderMessageId();

        emailChannel.dispatchDelivery(id);

        assertThat(deliveryRepository.findById(id).orElseThrow().getProviderMessageId()).isEqualTo(firstMessageId);
    }

    @Test
    @Tag("c05")
    void aRequestForAPersonThatDoesNotExistIsNotMistakenForADuplicate() {
        Person ghost = new Person();
        ghost.setId(987_654_321L);
        String key = uniqueKey();

        assertThatThrownBy(() -> deliveryService.enqueue(request(ghost, key, false))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countDeliveries(key)).isZero();
        assertThat(countNotifications(key)).isZero();
    }

    @Test
    @Tag("c09")
    @Tag("D-184")
    void aGatewayThatKeepsFailingLeavesTheDeliveryFailedAfterThreeAttemptsWithoutRaising() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        deliveryService.enqueue(request(student, key, false));
        Long id = deliveryOf(key, DeliveryChannel.EMAIL).getId();
        AtomicInteger sends = new AtomicInteger();
        TransactionalEmailGateway failing = (TransactionalEmail email) -> {
            sends.incrementAndGet();
            throw new TransactionalEmailDeliveryException("proveedor caido", null);
        };
        EmailDeliveryChannel failingChannel = new EmailDeliveryChannel(deliveryRepository, emailRecipientLookup, preferenceQuery, emailDeliveryPolicySet, failing);

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> failingChannel.dispatchDelivery(id))).doesNotThrowAnyException();
        NotificationDelivery afterFirst = deliveryRepository.findById(id).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> failingChannel.dispatchDelivery(id));
        transactionTemplate.executeWithoutResult(status -> failingChannel.dispatchDelivery(id));

        NotificationDelivery failed = deliveryRepository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getStatusReason()).isEqualTo("PROVIDER_ERROR");
        assertThat(sends.get()).isEqualTo(3);

        transactionTemplate.executeWithoutResult(status -> failingChannel.dispatchDelivery(id));
        assertThat(sends.get()).isEqualTo(3);
    }

    @Test
    @Tag("c09")
    @Tag("D-184")
    void aDeliveryThatFailedOnceIsSentByTheNextAttempt() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        deliveryService.enqueue(request(student, key, false));
        Long id = deliveryOf(key, DeliveryChannel.EMAIL).getId();
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway flaky = (TransactionalEmail email) -> {
            if (calls.incrementAndGet() == 1) {
                throw new TransactionalEmailDeliveryException("timeout", null);
            }
            return gateway.send(email);
        };
        EmailDeliveryChannel flakyChannel = new EmailDeliveryChannel(deliveryRepository, emailRecipientLookup, preferenceQuery, emailDeliveryPolicySet, flaky);

        transactionTemplate.executeWithoutResult(status -> flakyChannel.dispatchDelivery(id));
        transactionTemplate.executeWithoutResult(status -> flakyChannel.dispatchDelivery(id));

        NotificationDelivery sent = deliveryRepository.findById(id).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(sent.getAttempts()).isEqualTo(1);
        assertThat(sent.getStatusReason()).isNull();
    }

    @Test
    @Tag("c05")
    void theLargestValuesEveryColumnAcceptsAreStoredWithoutErrors() {
        Person student = persistPerson(true);
        String key = "k".repeat(120);
        DeliveryEnvelope envelope = new DeliveryEnvelope(key, DeliveryCategory.COLLECTION_REMINDER, "K".repeat(64), 1, student.getId(), "T".repeat(60), 1L, DAY);
        DeliveryContent longEmail = DeliveryContent.forEmail("s".repeat(300), "b".repeat(20_000), "h".repeat(20_000));
        DeliveryContent longInApp = DeliveryContent.forInApp("m".repeat(5_000));

        assertThatCode(() -> deliveryService.enqueue(new DeliveryRequest(envelope, NotificationType.COLLECTION_REMINDER, longInApp, longEmail, false)))
                .doesNotThrowAnyException();

        assertThat(countDeliveries(key)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select length(message) from notification where dedup_key = ?", Integer.class, key)).isEqualTo(1000);
    }

    @Test
    @Tag("c08")
    void theDatabaseRefusesAReasonlessSuppressionAndASentRowWithoutInstant() {
        Person student = persistPerson(true);
        String insert = "insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, status_reason, scheduled_for, "
                + "body_text, subject, sent_at, created_at) values (?, ?, 'COLLECTION_REMINDER', 'K', 1, ?, ?, ?, current_date, 'x', 'asunto', ?, now())";

        assertThatThrownBy(() -> jdbc.update(insert, uniqueKey(), "EMAIL", student.getId(), "SUPPRESSED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uniqueKey(), "EMAIL", student.getId(), "SENT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uniqueKey(), "IN_APP", student.getId(), "SENT_SIMULATED", null, java.sql.Timestamp.from(java.time.Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbc.update(insert, uniqueKey(), "EMAIL", student.getId(), "SUPPRESSED", "OPTED_OUT", null)).doesNotThrowAnyException();
    }

    @Test
    @Tag("c05")
    void theDatabaseRefusesTwoDeliveriesWithTheSameKeyOnTheSameChannelButAllowsOnePerChannel() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        String insert = "insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, scheduled_for, "
                + "body_text, subject, sent_at, created_at) values (?, ?, 'COLLECTION_REMINDER', 'K', 1, ?, 'QUEUED', current_date, 'x', 'asunto', null, now())";

        jdbc.update(insert, key, "EMAIL", student.getId());
        assertThatCode(() -> jdbc.update(insert, key, "IN_APP", student.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update(insert, key, "EMAIL", student.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("c05")
    void theDatabaseRefusesTwoNotificationsWithTheSameDedupKeyButAllowsAnyNumberWithout() {
        Person student = persistPerson(true);
        String key = uniqueKey();
        String insert = "insert into notification (recipient_id, type, message, read, created_at, dedup_key) values (?, 'COLLECTION_REMINDER', 'm', false, now(), ?)";

        jdbc.update(insert, student.getId(), key);
        assertThatThrownBy(() -> jdbc.update(insert, student.getId(), key)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, student.getId(), null);
        assertThatCode(() -> jdbc.update(insert, student.getId(), null)).doesNotThrowAnyException();
    }
}
