package com.red_escuela.backend.notification.delivery;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryDispatchJob;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchPlanningService;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchSettingsSource;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchSource;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailDeliveryException;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Queues and dispatches hundreds of mails per scenario, ten scenarios in one class")
@SpringBootTest
@ActiveProfiles("test")
class EmailDispatchCapIT extends AbstractIntegrationTest {
    private static final int HIGHEST = 1;
    private static final int LOWER = 3;

    @Autowired
    private NotificationDeliveryService deliveryService;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private BusinessDayCalendar calendar;
    @Autowired
    private EmailDeliveryChannel realChannel;
    @Autowired
    private TransactionalEmailGateway realGateway;
    @Autowired
    private PersonContactFinder personContactFinder;
    @Autowired
    private NotificationPreferenceFinder preferenceQuery;
    @Autowired
    private PolicySet<EmailDeliveryContext> emailDeliveryPolicySet;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<Long, List<DispatchItem>> itemsByDelivery = new HashMap<>();
    private final Set<Long> notApplicable = new HashSet<>();
    private final Set<Long> paidBetweenPlanAndSend = new HashSet<>();
    private final Map<Long, String> optedOut = new HashMap<>();
    private final Map<Long, DeliveryContent> trimmedContent = new HashMap<>();
    private Person recipient;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
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

    private Long queue(LocalDate day, int priority, LocalDate dueDate, boolean plannedBeforeDueDate) {
        if (recipient == null) {
            recipient = persistPerson();
        }
        int number = sequence.incrementAndGet();
        String key = "cap:" + System.nanoTime() + ":" + number;
        DeliveryEnvelope envelope = new DeliveryEnvelope(key, DeliveryCategory.COLLECTION_REMINDER, "TEST", priority, recipient.getId(), "TestReminder", (long) number, day);
        deliveryService.enqueue(new DeliveryRequest(envelope, NotificationType.COLLECTION_REMINDER, null,
                DeliveryContent.forEmail("Aviso " + key, "Texto " + key, "<p>Texto</p>"), false));
        Long id = deliveryRepository.findByDedupKeyAndChannel(key, DeliveryChannel.EMAIL).orElseThrow().getId();
        itemsByDelivery.put(id, List.of(new DispatchItem("item:" + id, dueDate, plannedBeforeDueDate, priority)));
        return id;
    }

    private List<Long> queueMany(int count, LocalDate day, int priority, LocalDate dueDate, boolean plannedBeforeDueDate) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(queue(day, priority, dueDate, plannedBeforeDueDate));
        }
        return ids;
    }

    private EmailDispatchSource source() {
        return new EmailDispatchSource() {
            @Override
            public List<DispatchItem> itemsOf(NotificationDelivery delivery) {
                return itemsByDelivery.getOrDefault(delivery.getId(), List.of());
            }

            @Override
            public Optional<DeliveryContent> contentWithoutItems(NotificationDelivery delivery, Set<String> itemKeys) {
                return Optional.ofNullable(trimmedContent.get(delivery.getId()));
            }

            @Override
            public Optional<String> suppressionReasonOf(NotificationDelivery delivery) {
                return Optional.ofNullable(optedOut.get(delivery.getId()));
            }

            @Override
            public boolean isStillApplicable(NotificationDelivery delivery, LocalDate date) {
                if (paidBetweenPlanAndSend.remove(delivery.getId())) {
                    notApplicable.add(delivery.getId());
                    return true;
                }
                return !notApplicable.contains(delivery.getId());
            }
        };
    }

    private EmailDeliveryDispatchJob jobWith(EmailDispatchSettingsSource settings, EmailDeliveryChannel channel) {
        EmailDispatchPlanningService planning = new EmailDispatchPlanningService(deliveryRepository, source(), MutableClock.INSTANCE, transactionTemplate);
        return new EmailDeliveryDispatchJob(deliveryRepository, planning, settings, channel, calendar, MutableClock.INSTANCE);
    }

    private EmailDeliveryDispatchJob jobWithCap(int cap) {
        return jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, cap), realChannel);
    }

    private EmailDeliveryChannel channelWith(TransactionalEmailGateway gateway) {
        return new EmailDeliveryChannel(deliveryRepository, personContactFinder, preferenceQuery, emailDeliveryPolicySet, gateway) {
            @Override
            public void dispatchDelivery(Long deliveryId) {
                transactionTemplate.executeWithoutResult(status -> super.dispatchDelivery(deliveryId));
            }
        };
    }

    private void at(LocalDate day, int hour, int minute) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(day, LocalTime.of(hour, minute)));
    }

    private Map<DeliveryStatus, Long> statusesOf(List<Long> ids) {
        return deliveryRepository.findAllById(ids).stream().collect(Collectors.groupingBy(NotificationDelivery::getStatus, Collectors.counting()));
    }

    private NotificationDelivery deliveryOf(Long id) {
        return deliveryRepository.findById(id).orElseThrow();
    }

    @Test
    @Tag("e01")
    @Tag("D-343")
    void threeHundredMailsWithACapOfTwoHundredFiftySendFortyAndTwoHundredTenAndDeferFifty() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        LocalDate tenth = LocalDate.of(2031, 3, 10);
        LocalDate eleventh = tenth.plusDays(1);
        List<Long> suspension = queueMany(40, tenth, HIGHEST, tenth.plusDays(10), false);
        List<Long> beforeDue = queueMany(260, tenth, LOWER, eleventh, true);
        EmailDeliveryDispatchJob job = jobWithCap(250);

        at(tenth, 9, 0);
        job.dispatchDueEmails();

        assertThat(statusesOf(suspension)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        assertThat(statusesOf(beforeDue)).containsEntry(DeliveryStatus.SENT_SIMULATED, 210L).containsEntry(DeliveryStatus.DEFERRED, 50L);
        List<Long> sentOfBeforeDue = beforeDue.stream().filter(id -> deliveryOf(id).getStatus() == DeliveryStatus.SENT_SIMULATED).toList();
        assertThat(sentOfBeforeDue).isEqualTo(beforeDue.subList(0, 210));
        List<NotificationDelivery> deferred = beforeDue.subList(210, 260).stream().map(this::deliveryOf).toList();
        assertThat(deferred).allSatisfy(delivery -> {
            assertThat(delivery.getStatusReason()).isEqualTo("DAILY_CAP_REACHED");
            assertThat(delivery.getScheduledFor()).isEqualTo(eleventh);
        });

        at(tenth, 9, 10);
        job.dispatchDueEmails();
        assertThat(statusesOf(beforeDue)).containsEntry(DeliveryStatus.SENT_SIMULATED, 210L).containsEntry(DeliveryStatus.DEFERRED, 50L);

        at(eleventh, 9, 0);
        job.dispatchDueEmails();
        assertThat(statusesOf(beforeDue)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        assertThat(invariants.noEmailIsSentTwice()).isEmpty();
    }

    @Test
    @Tag("e02")
    @Tag("D-343")
    void mailsThatDoNotFitOnTheDueDateEitherAreDiscardedTheNextDay() {
        LocalDate tenth = LocalDate.of(2032, 3, 10);
        LocalDate eleventh = tenth.plusDays(1);
        LocalDate twelfth = tenth.plusDays(2);
        List<Long> deferredOnTheTenth = queueMany(50, tenth, LOWER, eleventh, true);
        List<Long> firstInLine = queueMany(250, tenth, HIGHEST, tenth.plusDays(10), false);
        EmailDeliveryDispatchJob job = jobWithCap(250);
        at(tenth, 9, 0);
        job.dispatchDueEmails();
        assertThat(statusesOf(deferredOnTheTenth)).containsOnlyKeys(DeliveryStatus.DEFERRED);
        assertThat(statusesOf(firstInLine)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        List<Long> competingOnTheEleventh = queueMany(250, eleventh, HIGHEST, tenth.plusDays(10), false);

        at(eleventh, 9, 0);
        job.dispatchDueEmails();
        assertThat(statusesOf(deferredOnTheTenth)).containsOnlyKeys(DeliveryStatus.DEFERRED);
        assertThat(statusesOf(competingOnTheEleventh)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(deferredOnTheTenth.get(0)).getScheduledFor()).isEqualTo(twelfth);

        at(twelfth, 9, 0);
        job.dispatchDueEmails();
        assertThat(statusesOf(deferredOnTheTenth)).containsOnlyKeys(DeliveryStatus.DISCARDED);
        assertThat(deferredOnTheTenth).allSatisfy(id -> assertThat(deliveryOf(id).getStatusReason()).isEqualTo("NO_LONGER_TIMELY"));
    }

    @Test
    @Tag("e07")
    @Tag("D-343")
    void theCapApprovedTheDayBeforeRulesTheNextDispatch() {
        LocalDate third = LocalDate.of(2033, 3, 8);
        LocalDate fourth = third.plusDays(1);
        Map<LocalDate, Integer> capByDay = Map.of(third, 250, fourth, 280);
        EmailDeliveryDispatchJob job = jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, capByDay.get(date)), realChannel);
        List<Long> mails = queueMany(300, fourth, LOWER, fourth.plusDays(30), false);

        at(fourth, 9, 0);
        job.dispatchDueEmails();

        assertThat(statusesOf(mails)).containsEntry(DeliveryStatus.SENT_SIMULATED, 280L).containsEntry(DeliveryStatus.DEFERRED, 20L);
    }

    @Test
    @Tag("e04")
    @Tag("D-354")
    void aMailThatIsNoLongerApplicableIsSuppressedAndDoesNotConsumeTheCap() {
        LocalDate day = LocalDate.of(2034, 3, 7);
        Long paid = queue(day, HIGHEST, day.plusDays(5), true);
        Long second = queue(day, LOWER, day.plusDays(5), true);
        Long third = queue(day, LOWER, day.plusDays(5), true);
        notApplicable.add(paid);

        at(day, 9, 0);
        jobWithCap(2).dispatchDueEmails();

        assertThat(deliveryOf(paid).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(deliveryOf(paid).getStatusReason()).isEqualTo("NO_LONGER_APPLICABLE");
        assertThat(deliveryOf(second).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(third).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("e04")
    @Tag("D-354")
    void aMailPaidBetweenThePlanAndTheSendingIsSuppressedAndNeverSent() {
        LocalDate day = LocalDate.of(2034, 3, 9);
        Long paidLater = queue(day, HIGHEST, day.plusDays(5), true);
        Long other = queue(day, LOWER, day.plusDays(5), true);
        paidBetweenPlanAndSend.add(paidLater);
        List<String> sentSubjects = new ArrayList<>();
        TransactionalEmailGateway recording = (TransactionalEmail email) -> {
            sentSubjects.add(email.subject());
            return realGateway.send(email);
        };
        EmailDeliveryDispatchJob job = jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250), channelWith(recording));

        at(day, 9, 0);
        job.dispatchDueEmails();

        assertThat(deliveryOf(paidLater).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(deliveryOf(paidLater).getStatusReason()).isEqualTo("NO_LONGER_APPLICABLE");
        assertThat(deliveryOf(other).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(sentSubjects).hasSize(1).doesNotContain(deliveryOf(paidLater).getSubject());
    }

    @Test
    @Tag("D-340")
    @Tag("D-372")
    void aMailThatTheSourceReportsAsOptedOutIsSuppressedAndDoesNotEnterThePlan() {
        LocalDate day = LocalDate.of(2034, 3, 10);
        Long optedOutMail = queue(day, HIGHEST, day.plusDays(5), true);
        Long other = queue(day, LOWER, day.plusDays(5), true);
        optedOut.put(optedOutMail, "OPTED_OUT");

        at(day, 9, 0);
        jobWithCap(1).dispatchDueEmails();

        assertThat(deliveryOf(optedOutMail).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(deliveryOf(optedOutMail).getStatusReason()).isEqualTo("OPTED_OUT");
        assertThat(deliveryOf(other).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    @Tag("c06")
    @Tag("D-342")
    void theWindowComesFromTheSettingsOfTheDay() {
        LocalDate day = LocalDate.of(2034, 3, 13);
        Long mail = queue(day, LOWER, day.plusDays(5), false);
        EmailDispatchWindow afternoon = new EmailDispatchWindow(LocalTime.of(14, 0), LocalTime.of(16, 0));
        EmailDeliveryDispatchJob job = jobWith(date -> new EmailDispatchSettings(afternoon, 250), realChannel);

        at(day, 9, 30);
        job.dispatchDueEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        at(day, 14, 0);
        job.dispatchDueEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        at(day, 16, 0);
        assertThat(job.dispatchDueEmails()).isZero();
    }

    @Test
    @Tag("D-343")
    @Tag("D-372")
    void aDeferredMailKeepsTheItemsStillInTimeAndSendsTheTrimmedText() {
        LocalDate day = LocalDate.of(2034, 3, 8);
        Long mail = queue(day, LOWER, day.plusDays(9), false);
        itemsByDelivery.put(mail, List.of(new DispatchItem("expired", day.minusDays(1), true, LOWER), new DispatchItem("alive", day.plusDays(9), false, LOWER)));
        trimmedContent.put(mail, DeliveryContent.forEmail("Aviso recortado", "Solo el vigente", "<p>Solo el vigente</p>"));

        at(day, 9, 0);
        jobWithCap(10).dispatchDueEmails();

        NotificationDelivery sent = deliveryOf(mail);
        assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(sent.getSubject()).isEqualTo("Aviso recortado");
        assertThat(sent.getBodyText()).isEqualTo("Solo el vigente");
    }

    @Test
    @Tag("e05")
    void aGatewayThatFailsTwiceSendsOnTheThirdAttemptInsideTheWindow() {
        LocalDate day = LocalDate.of(2035, 3, 6);
        Long mail = queue(day, LOWER, day.plusDays(5), false);
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway flaky = (TransactionalEmail email) -> {
            if (calls.incrementAndGet() <= 2) {
                throw new TransactionalEmailDeliveryException("timeout", null);
            }
            return realGateway.send(email);
        };
        EmailDeliveryDispatchJob job = jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250), channelWith(flaky));

        at(day, 9, 0);
        job.dispatchDueEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        at(day, 9, 10);
        job.dispatchDueEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        at(day, 9, 20);
        job.dispatchDueEmails();

        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(mail).getAttempts()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @Tag("e05")
    void threeFailuresLeaveTheMailFailedAndWarn() {
        LocalDate day = LocalDate.of(2035, 3, 7);
        Long mail = queue(day, LOWER, day.plusDays(5), false);
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway broken = (TransactionalEmail email) -> {
            calls.incrementAndGet();
            throw new TransactionalEmailDeliveryException("proveedor caido", null);
        };
        EmailDeliveryDispatchJob job = jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250), channelWith(broken));
        Logger channelLogger = (Logger) LoggerFactory.getLogger(EmailDeliveryChannel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        channelLogger.addAppender(appender);
        try {
            at(day, 9, 0);
            job.dispatchDueEmails();
            at(day, 9, 10);
            job.dispatchDueEmails();
            at(day, 9, 20);
            job.dispatchDueEmails();
            at(day, 9, 30);
            job.dispatchDueEmails();
        } finally {
            channelLogger.detachAppender(appender);
        }

        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(deliveryOf(mail).getAttempts()).isEqualTo(3);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("Mail de cobranza fallido");
        });
    }

    @Test
    @Tag("e06")
    @Tag("D-343")
    void twoInstancesRunningAtTheSameTimeNeverSendTheSameMailTwice() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        LocalDate day = LocalDate.of(2036, 3, 4);
        List<Long> mails = queueMany(120, day, LOWER, day.plusDays(20), false);
        Map<String, AtomicInteger> sendsBySubject = new ConcurrentHashMap<>();
        TransactionalEmailGateway counting = (TransactionalEmail email) -> {
            sendsBySubject.computeIfAbsent(email.subject(), subject -> new AtomicInteger()).incrementAndGet();
            sleepBriefly();
            return realGateway.send(email);
        };
        EmailDeliveryChannel channel = channelWith(counting);
        List<EmailDeliveryDispatchJob> instances = List.of(
                jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250), channel),
                jobWith(date -> new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250), channel));
        at(day, 9, 0);
        CyclicBarrier startTogether = new CyclicBarrier(instances.size());
        ExecutorService pool = Executors.newFixedThreadPool(instances.size());
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int position = 0; position < instances.size(); position++) {
                EmailDeliveryDispatchJob instance = instances.get(position);
                long startDelayMillis = position * 300L;
                runs.add(pool.submit(() -> {
                    startTogether.await(30, TimeUnit.SECONDS);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(startDelayMillis));
                    return instance.dispatchDueEmails();
                }));
            }
            for (Future<Integer> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statusesOf(mails)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        assertThat(sendsBySubject.values()).hasSize(120).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(invariants.noEmailIsSentTwice()).isEmpty();
    }

    @Test
    @Tag("D-343")
    void theSentCounterOfTheDayIsDiscountedFromTheCap() {
        LocalDate day = LocalDate.of(2037, 3, 3);
        List<Long> morning = queueMany(2, day, LOWER, day.plusDays(20), false);
        at(day, 9, 0);
        jobWithCap(3).dispatchDueEmails();
        List<Long> later = queueMany(3, day, LOWER, day.plusDays(20), false);

        at(day, 9, 10);
        jobWithCap(3).dispatchDueEmails();

        assertThat(statusesOf(morning)).containsOnlyKeys(DeliveryStatus.SENT_SIMULATED);
        assertThat(statusesOf(later)).containsEntry(DeliveryStatus.SENT_SIMULATED, 1L).containsEntry(DeliveryStatus.DEFERRED, 2L);
    }

    @Test
    @Tag("D-343")
    void theFirstPriorityGoesFirstEvenWhenItWasQueuedLast() {
        LocalDate day = LocalDate.of(2038, 3, 2);
        Long late = queue(day, LOWER, day.plusDays(20), false);
        Long urgent = queue(day, HIGHEST, day.plusDays(20), false);

        at(day, 9, 0);
        jobWithCap(1).dispatchDueEmails();

        assertThat(deliveryOf(urgent).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(late).getStatus()).isEqualTo(DeliveryStatus.DEFERRED);
    }

    @Test
    @Tag("D-343")
    void theInvariantFlagsTheSameMailSentTwice() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        LocalDate day = LocalDate.of(2038, 3, 3);
        Long first = queue(day, LOWER, day.plusDays(20), false);
        Long second = queue(day, LOWER, day.plusDays(20), false);
        try {
            assertThat(invariants.noEmailIsSentTwice()).isEmpty();
            jdbc.update("update notification_delivery set status = 'SENT_SIMULATED', status_reason = null, sent_at = now(), related_entity_id = 999999, subject = 'Igual', body_text = 'Igual' where id in (?, ?)",
                    first, second);

            assertThat(invariants.noEmailIsSentTwice()).hasSize(1).first().asString().contains("sent 2 times");
        } finally {
            jdbc.update("delete from notification_delivery where id in (?, ?)", first, second);
        }
    }

    private static void sleepBriefly() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(8));
    }
}
