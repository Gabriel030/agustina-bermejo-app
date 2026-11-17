package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastConsentRecheck;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractBroadcastDispatchIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    protected NotificationDeliveryEnqueuer deliveryEnqueuer;
    @Autowired
    protected NotificationDeliveryRepository deliveryRepository;
    @Autowired
    protected TransactionalEmailGateway realGateway;
    @Autowired
    protected ContactConsentLedger consentLedger;
    @Autowired
    protected BroadcastConsentRecheck consentRecheck;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PersonContactFinder personContactFinder;
    @Autowired
    private NotificationPreferenceFinder preferenceFinder;
    @Autowired
    private PolicySet<EmailDeliveryContext> emailDeliveryPolicySet;
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;

    protected final List<String> sentSubjects = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startWithNoDeliveries() {
        jdbc.update("delete from notification_delivery");
        sentSubjects.clear();
    }

    @AfterEach
    void stopTime() {
        MutableClock.INSTANCE.reset();
    }

    protected Person persistPerson() {
        String dni = "6" + System.nanoTime();
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

    protected void at(LocalDate day, int hour, int minute) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(hour, minute)));
    }

    protected Long queueBroadcast(Person person, BroadcastCategory category, LocalDate day) {
        long number = SEQUENCE.incrementAndGet();
        DeliveryEnvelope envelope = new DeliveryEnvelope("BCT:" + number, DeliveryCategory.BROADCAST, BroadcastDeliveryKind.of(category), 5,
                person.getId(), "BROADCAST", number, day);
        return enqueue(envelope, "Masivo " + number);
    }

    protected List<Long> queueBroadcasts(Person person, int count, LocalDate day) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(queueBroadcast(person, BroadcastCategory.INSTITUTIONAL, day));
        }
        return ids;
    }

    protected void sendTransactional(Person person, DeliveryCategory category, int count, LocalDate day) {
        EmailDeliveryChannel channel = recordingChannel(realGateway);
        for (int i = 0; i < count; i++) {
            long number = SEQUENCE.incrementAndGet();
            DeliveryEnvelope envelope = new DeliveryEnvelope("BCT:" + number, category, "TEST_TRANSACTIONAL", 1, person.getId(),
                    "Test", number, day);
            channel.dispatchDelivery(enqueue(envelope, "Transaccional " + number));
        }
        sentSubjects.removeIf(subject -> subject.startsWith("Transaccional"));
    }

    private Long enqueue(DeliveryEnvelope envelope, String subject) {
        return deliveryEnqueuer.enqueueEmailAndFindDeliveryId(
                new DeliveryRequest(envelope, null, null, DeliveryContent.forEmail(subject, "Texto " + subject, null), false)).orElseThrow();
    }

    protected void revokeConsent(Person person, BroadcastCategory category) {
        transactionTemplate.executeWithoutResult(status ->
                consentLedger.recordConsent(person.getId(), ConsentChannel.EMAIL, category, false, ConsentSource.PROFILE, null));
    }

    protected void grantConsent(Person person, BroadcastCategory category) {
        transactionTemplate.executeWithoutResult(status ->
                consentLedger.recordConsent(person.getId(), ConsentChannel.EMAIL, category, true, ConsentSource.PROFILE, null));
    }

    protected EmailDeliveryChannel recordingChannel(TransactionalEmailGateway delegate) {
        TransactionalEmailGateway recording = (TransactionalEmail email) -> {
            TransactionalEmailGateway gateway = delegate;
            var receipt = gateway.send(email);
            sentSubjects.add(email.subject());
            return receipt;
        };
        return new EmailDeliveryChannel(deliveryRepository, personContactFinder, preferenceFinder, emailDeliveryPolicySet, recording,
                circuitBreakerFactory) {
            @Override
            public void dispatchDelivery(Long deliveryId) {
                transactionTemplate.executeWithoutResult(status -> super.dispatchDelivery(deliveryId));
            }
        };
    }

    protected BroadcastEmailDispatchJob jobWith(int dailyLimit, int reserved, int maxPerRun, EmailDeliveryChannel channel) {
        BroadcastDispatchSettings settings = new BroadcastDispatchSettings(dailyLimit, reserved, maxPerRun, "09:00", "20:00");
        return new BroadcastEmailDispatchJob(deliveryRepository, channel, consentRecheck, settings, MutableClock.INSTANCE,
                new SimpleMeterRegistry());
    }

    protected NotificationDelivery deliveryOf(Long id) {
        return deliveryRepository.findById(id).orElseThrow();
    }

    protected long sentOn(LocalDate day, DeliveryCategory category) {
        return jdbc.queryForObject("""
                select count(*) from notification_delivery
                where category = ? and status in ('SENT', 'SENT_SIMULATED') and (sent_at at time zone 'America/Argentina/Buenos_Aires')::date = ?""",
                Long.class, category.name(), java.sql.Date.valueOf(day));
    }

    protected long count(String where) {
        return jdbc.queryForObject("select count(*) from notification_delivery where " + where, Long.class);
    }
}
