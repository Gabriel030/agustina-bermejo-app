package com.red_escuela.backend.notification.invitation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotRunningException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.notification.invitation.specialist.ActivationInvitationEmailFactory;
import com.red_escuela.backend.notification.invitation.specialist.InvitationActivationTokenRequester;
import com.red_escuela.backend.notification.invitation.specialist.InvitationDeliveryRecorder;
import com.red_escuela.backend.notification.invitation.specialist.InvitationFailedRetryTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationMailSender;
import com.red_escuela.backend.notification.invitation.specialist.InvitationRecipientSendTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationSendingCapacity;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveSettings;
import com.red_escuela.backend.notification.invitation.usecase.CancelInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.CreateInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.PauseInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.SendInvitationToRecipientUseCase;
import com.red_escuela.backend.notification.invitation.usecase.StartInvitationWaveUseCase;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.mail.EmailDeliveryReceipt;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailDeliveryException;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.mail.TransactionalEmailRejectedException;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadSettings;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerSettings;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InvitationSendIT extends AbstractIntegrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/activar\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private CreateInvitationWaveUseCase createInvitationWave;
    @Autowired
    private StartInvitationWaveUseCase startInvitationWave;
    @Autowired
    private PauseInvitationWaveUseCase pauseInvitationWave;
    @Autowired
    private CancelInvitationWaveUseCase cancelInvitationWave;
    @Autowired
    private SendInvitationToRecipientUseCase sendInvitationToRecipient;
    @Autowired
    private InvitationWaveRepository waveRepository;
    @Autowired
    private InvitationWaveRecipientRepository recipientRepository;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private PersonContactFinder personContactFinder;
    @Autowired
    private InvitationActivationTokenRequester tokenRequester;
    @Autowired
    private ActivationInvitationEmailFactory emailFactory;
    @Autowired
    private InvitationDeliveryRecorder deliveryRecorder;
    @Autowired
    private InvitationSendingCapacity capacity;
    @Autowired
    private InvitationWaveSettings settings;
    @Autowired
    private InvitationFailedRetryTransaction retryTransaction;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Clock clock;
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private Long directorId;
    private long batchId;
    private String suffix;
    private final List<TransactionalEmail> sentEmails = new CopyOnWriteArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void prepare() {
        cleanTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        directorId = transactions.execute(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            return AudienceFreezeFixtures.person(entityManager, roleRepository, "send-director-" + suffix, true, "DIRECTOR").getId();
        });
        batchId = jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at, version)
                values ('Lote ' || ?, 'COMMITTED', (select id from institution limit 1), ?, now(), 0) returning id
                """, Long.class, suffix, directorId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(directorId), null, List.of()));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach
    void restore() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        SecurityContextHolder.clearContext();
        cleanTables();
    }

    private void cleanTables() {
        jdbc.update("delete from invitation_wave_recipient");
        jdbc.update("delete from invitation_wave");
        jdbc.update("delete from notification_delivery where category = 'INVITATION'");
        jdbc.update("delete from activation_token where person_id in (select id from person where last_name like 'Send-%')");
        jdbc.update("delete from import_created_entity where batch_id in (select id from import_batch where name like 'Lote %')");
        jdbc.update("delete from import_batch where name like 'Lote %'");
    }

    private long importedStudent() {
        long personId = transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            String unique = UUID.randomUUID().toString();
            Person person = new Person();
            person.setName("Importada");
            person.setLastname("Send-" + suffix);
            person.setDni(unique.substring(0, 12));
            person.setUsername("send." + unique);
            person.setEmail("send." + unique + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.setGrantSystemAccess(false);
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return person.getId();
        });
        jdbc.update("""
                insert into import_created_entity (batch_id, entity_type, entity_id, natural_key, created_at)
                values (?, 'PERSONS', ?, ?, now())
                """, batchId, personId, "dni-" + batchId + "-" + personId);
        return personId;
    }

    private long runningWave(int dailyCap) {
        long waveId = createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada " + suffix, 1, 15, dailyCap, batchId)).id();
        startInvitationWave.startInvitationWave(waveId, directorId);
        return waveId;
    }

    private long recipientOf(long waveId, long personId) {
        return jdbc.queryForObject("select id from invitation_wave_recipient where wave_id = ? and person_id = ?", Long.class, waveId, personId);
    }

    private String recipientColumn(long recipientId, String column) {
        return jdbc.queryForObject("select " + column + "::text from invitation_wave_recipient where id = ?", String.class, recipientId);
    }

    private TransactionalEmailGateway recordingGateway() {
        return email -> {
            sentEmails.add(email);
            return new EmailDeliveryReceipt("provider-" + UUID.randomUUID().toString().substring(0, 8), false);
        };
    }

    private TransactionalEmailGateway failingGateway(RuntimeException failure) {
        return email -> {
            throw failure;
        };
    }

    private CircuitBreaker disabledBreaker() {
        return new CircuitBreaker("mail", new CircuitBreakerSettings(false, 2, 1, Duration.ofMinutes(1)), clock, new SimpleMeterRegistry());
    }

    private CircuitBreaker tripWireBreaker() {
        return new CircuitBreaker("mail", new CircuitBreakerSettings(true, 2, 1, Duration.ofMinutes(5)), clock, new SimpleMeterRegistry());
    }

    private Bulkhead looseBulkhead() {
        return new Bulkhead("mail", new BulkheadSettings(false, 4, Duration.ZERO), new SimpleMeterRegistry());
    }

    private InvitationRecipientSendTransaction senderWith(TransactionalEmailGateway gateway, CircuitBreaker breaker) {
        return new InvitationRecipientSendTransaction(waveRepository, recipientRepository, deliveryRepository, personContactFinder, tokenRequester,
                emailFactory, new InvitationMailSender(gateway, breaker, looseBulkhead()), deliveryRecorder, capacity, clock);
    }

    private InvitationSendResult send(InvitationRecipientSendTransaction sender, long recipientId) {
        try {
            return transactions.execute(status -> sender.sendInvitationToRecipient(recipientId));
        } catch (InvitationSendDeferredException deferred) {
            return InvitationSendResult.DEFERRED;
        }
    }

    private String tokenOf(TransactionalEmail email) {
        Matcher matcher = TOKEN_IN_LINK.matcher(email.bodyText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void happyPathSendsOneMailWithAnActivationLinkThatRedeemsAndMarksTheRecipientSent() throws Exception {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);

        InvitationSendResult result = send(senderWith(recordingGateway(), disabledBreaker()), recipientId);

        assertThat(result).isEqualTo(InvitationSendResult.SENT);
        assertThat(recipientColumn(recipientId, "status")).isEqualTo("SENT");
        assertThat(recipientColumn(recipientId, "attempts")).isEqualTo("1");
        assertThat(recipientColumn(recipientId, "sent_at")).isNotNull();
        assertThat(sentEmails).hasSize(1);
        TransactionalEmail email = sentEmails.get(0);
        assertThat(email.bodyText()).contains(frontendBaseUrl + "/activar?token=");
        assertThat(email.unsubscribeUrl()).isNull();
        assertThat(jdbc.queryForObject("select status || '/' || category || '/' || dedup_key from notification_delivery where id = ?", String.class,
                Long.valueOf(recipientColumn(recipientId, "delivery_id"))))
                .isEqualTo("SENT/INVITATION/INVITATION:" + waveId + ":" + personId + ":1");
        mockMvc.perform(post("/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenOf(email) + "\",\"newPassword\":\"Brand-New-Secret-5678\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("select password is not null from person where id = ?", Boolean.class, personId)).isTrue();
    }

    @Test
    void theClearTokenIsNeverPersistedNorLogged() {
        long personId = importedStudent();
        long waveId = runningWave(10);

        send(senderWith(recordingGateway(), disabledBreaker()), recipientOf(waveId, personId));

        String token = tokenOf(sentEmails.get(0));
        List<String> tables = jdbc.queryForList("select table_name from information_schema.tables where table_schema = current_schema() and table_type = 'BASE TABLE'", String.class);
        List<String> tablesWithToken = new ArrayList<>();
        for (String table : tables) {
            Integer hits = jdbc.queryForObject("select count(*) from \"" + table + "\" x where x::text like ?", Integer.class, "%" + token + "%");
            if (hits != null && hits > 0) {
                tablesWithToken.add(table);
            }
        }
        assertThat(tablesWithToken).isEmpty();
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(token));
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?", Integer.class, personId)).isEqualTo(1);
    }

    @Test
    void aPersonWhoActivatedBeforeTheSendBecomesActivatedWithoutAnyMail() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        jdbc.update("update person set password = 'hash' where id = ?", personId);

        InvitationSendResult result = send(senderWith(recordingGateway(), disabledBreaker()), recipientId);

        assertThat(result).isEqualTo(InvitationSendResult.ACTIVATED);
        assertThat(recipientColumn(recipientId, "status")).isEqualTo("ACTIVATED");
        assertThat(sentEmails).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'INVITATION' and status in ('SENT','SENT_SIMULATED')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?", Integer.class, personId)).isZero();
    }

    @Test
    void nothingIsSentWhenTheWaveIsPausedAndRecipientsExcludedByCancellationAreNeverSent() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        pauseInvitationWave.pauseInvitationWave(waveId, directorId);

        assertThat(send(senderWith(recordingGateway(), disabledBreaker()), recipientId)).isEqualTo(InvitationSendResult.NOT_SENDABLE);
        cancelInvitationWave.cancelInvitationWave(waveId, directorId);
        assertThat(send(senderWith(recordingGateway(), disabledBreaker()), recipientId)).isEqualTo(InvitationSendResult.NOT_SENDABLE);

        assertThat(sentEmails).isEmpty();
        assertThat(recipientColumn(recipientId, "status")).isEqualTo("EXCLUDED");
        assertThat(recipientColumn(recipientId, "exclusion_reason")).isEqualTo("WAVE_CANCELLED");
    }

    @Test
    void anInactivePersonIsExcludedAsNotEligibleAndItsQueuedDeliveryIsDiscarded() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        jdbc.update("update person set status = 'INACTIVE' where id = ?", personId);

        InvitationSendResult result = send(senderWith(recordingGateway(), disabledBreaker()), recipientId);

        assertThat(result).isEqualTo(InvitationSendResult.EXCLUDED);
        assertThat(recipientColumn(recipientId, "exclusion_reason")).isEqualTo("NOT_ELIGIBLE");
        assertThat(sentEmails).isEmpty();
        assertThat(jdbc.queryForObject("select status from notification_delivery where id = ?", String.class,
                Long.valueOf(recipientColumn(recipientId, "delivery_id")))).isEqualTo("DISCARDED");
    }

    @Test
    void aPersonWhoLostTheEmailIsExcludedAsNoEmail() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        jdbc.update("update person set email = null where id = ?", personId);

        InvitationSendResult result = send(senderWith(recordingGateway(), disabledBreaker()), recipientId);

        assertThat(result).isEqualTo(InvitationSendResult.EXCLUDED);
        assertThat(recipientColumn(recipientId, "exclusion_reason")).isEqualTo("NO_EMAIL");
        assertThat(sentEmails).isEmpty();
    }

    @Test
    void anOpenCircuitDefersWithoutConsumingAnAttemptOrCreatingDeliveries() {
        long first = importedStudent();
        long second = importedStudent();
        long waveId = runningWave(10);
        CircuitBreaker breaker = tripWireBreaker();
        InvitationRecipientSendTransaction sender = senderWith(failingGateway(new TransactionalEmailDeliveryException("down", null)), breaker);
        long firstRecipient = recipientOf(waveId, first);
        long secondRecipient = recipientOf(waveId, second);

        assertThat(send(sender, firstRecipient)).isEqualTo(InvitationSendResult.FAILED);
        assertThat(send(sender, secondRecipient)).isEqualTo(InvitationSendResult.DEFERRED);

        assertThat(recipientColumn(secondRecipient, "status")).isEqualTo("PENDING");
        assertThat(recipientColumn(secondRecipient, "attempts")).isEqualTo("0");
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'INVITATION' and recipient_person_id = ?", Integer.class, second)).isZero();
    }

    @Test
    void aCircuitThatOpensDuringTheCallRollsBackTheAttempt() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        InvitationRecipientSendTransaction sender = senderWith(failingGateway(new CircuitOpenException("mail", Duration.ofSeconds(5))), disabledBreaker());

        assertThat(send(sender, recipientId)).isEqualTo(InvitationSendResult.DEFERRED);

        assertThat(recipientColumn(recipientId, "status")).isEqualTo("PENDING");
        assertThat(recipientColumn(recipientId, "attempts")).isEqualTo("0");
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'INVITATION'", Integer.class)).isZero();
    }

    @Test
    void providerFailuresMapToErrorCodesWithoutKeepingTheProviderMessage() {
        long rejectedPerson = importedStudent();
        long unavailablePerson = importedStudent();
        long timeoutPerson = importedStudent();
        long waveId = runningWave(10);
        long rejected = recipientOf(waveId, rejectedPerson);
        long unavailable = recipientOf(waveId, unavailablePerson);
        long timedOut = recipientOf(waveId, timeoutPerson);
        String leakyMessage = "rejected recipient leak@example.com";

        send(senderWith(failingGateway(new TransactionalEmailRejectedException(leakyMessage, 400, null)), disabledBreaker()), rejected);
        send(senderWith(failingGateway(new TransactionalEmailDeliveryException("Brevo fallo con status 503", null)), disabledBreaker()), unavailable);
        send(senderWith(failingGateway(new TransactionalEmailDeliveryException("Brevo no respondio a tiempo", null)), disabledBreaker()), timedOut);

        assertThat(recipientColumn(rejected, "status")).isEqualTo("FAILED");
        assertThat(recipientColumn(rejected, "last_error_code")).isEqualTo("PROVIDER_REJECTED");
        assertThat(recipientColumn(unavailable, "last_error_code")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(recipientColumn(timedOut, "last_error_code")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave_recipient where last_error_code like '%@%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'INVITATION' and status = 'FAILED'", Integer.class)).isEqualTo(3);
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains("leak@example.com"));
    }

    @Test
    void anExhaustedDailyCapOfTheWaveBlocksFurtherSends() {
        long first = importedStudent();
        long second = importedStudent();
        long waveId = runningWave(1);
        InvitationRecipientSendTransaction sender = senderWith(recordingGateway(), disabledBreaker());

        assertThat(send(sender, recipientOf(waveId, first))).isEqualTo(InvitationSendResult.SENT);
        long secondRecipient = recipientOf(waveId, second);
        assertThat(send(sender, secondRecipient)).isEqualTo(InvitationSendResult.NO_QUOTA);

        assertThat(sentEmails).hasSize(1);
        assertThat(recipientColumn(secondRecipient, "status")).isEqualTo("PENDING");
        assertThat(recipientColumn(secondRecipient, "attempts")).isEqualTo("0");
    }

    @Test
    void invitationsCountAgainstTheGlobalDailyLimit() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        int limit = settings.globalDailyLimit();
        jdbc.update("""
                insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, scheduled_for, attempts, subject, body_text, sent_at, created_at)
                select 'FILL:' || ? || ':' || g, 'EMAIL', 'INVITATION', 'FILL', 1, ?, 'SENT', current_date, 0, 'x', 'x', now(), now()
                from generate_series(1, ?) g
                """, suffix, directorId, limit);

        assertThat(send(senderWith(recordingGateway(), disabledBreaker()), recipientId)).isEqualTo(InvitationSendResult.NO_QUOTA);

        assertThat(sentEmails).isEmpty();
    }

    @Test
    void aFailedRecipientIsRetriedOnceWithANewAttemptAndRespectsTheMaxAttempts() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        send(senderWith(failingGateway(new TransactionalEmailDeliveryException("down", null)), disabledBreaker()), recipientId);
        assertThat(recipientColumn(recipientId, "status")).isEqualTo("FAILED");

        assertThat(retryTransactionRun(waveId)).isEqualTo(1);
        assertThat(retryTransactionRun(waveId)).isZero();
        assertThat(recipientColumn(recipientId, "status")).isEqualTo("QUEUED");
        assertThat(recipientColumn(recipientId, "attempts")).isEqualTo("2");
        assertThat(send(senderWith(recordingGateway(), disabledBreaker()), recipientId)).isEqualTo(InvitationSendResult.SENT);

        assertThat(recipientColumn(recipientId, "status")).isEqualTo("SENT");
        assertThat(sentEmails).hasSize(1);
        assertThat(jdbc.queryForList("select dedup_key from notification_delivery where category = 'INVITATION' order by id", String.class))
                .containsExactly("INVITATION:" + waveId + ":" + personId + ":1", "INVITATION:" + waveId + ":" + personId + ":2");
    }

    @Test
    void retryDoesNotTouchRecipientsThatReachedTheMaxAttempts() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        send(senderWith(failingGateway(new TransactionalEmailDeliveryException("down", null)), disabledBreaker()), recipientId);
        jdbc.update("update invitation_wave_recipient set attempts = ? where id = ?", settings.maxAttempts(), recipientId);

        assertThat(retryTransactionRun(waveId)).isZero();

        assertThat(recipientColumn(recipientId, "status")).isEqualTo("FAILED");
    }

    @Test
    void retryIsRejectedWhenTheWaveIsNotRunning() {
        importedStudent();
        long waveId = runningWave(10);
        pauseInvitationWave.pauseInvitationWave(waveId, directorId);

        assertThatThrownBy(() -> retryTransactionRun(waveId)).isInstanceOf(InvitationWaveNotRunningException.class);
    }

    @Test
    void retryStopsAtTheRemainingDailyCapacity() {
        long first = importedStudent();
        long second = importedStudent();
        long waveId = runningWave(1);
        InvitationRecipientSendTransaction failing = senderWith(failingGateway(new TransactionalEmailDeliveryException("down", null)), disabledBreaker());
        send(failing, recipientOf(waveId, first));
        send(failing, recipientOf(waveId, second));

        assertThat(retryTransactionRun(waveId)).isEqualTo(1);
        assertThat(retryTransactionRun(waveId)).isZero();
    }

    private int retryTransactionRun(long waveId) {
        return transactions.execute(status -> retryTransaction.retryFailedInvitations(waveId));
    }

    @Test
    void concurrentInvocationsForTheSameRecipientSendASingleMail() throws Exception {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway slow = email -> {
            calls.incrementAndGet();
            sentEmails.add(email);
            return new EmailDeliveryReceipt("provider-concurrent", false);
        };
        InvitationRecipientSendTransaction sender = senderWith(slow, disabledBreaker());
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<InvitationSendResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return send(sender, recipientId);
                }));
            }
            List<InvitationSendResult> results = new ArrayList<>();
            for (Future<InvitationSendResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).containsExactlyInAnyOrder(InvitationSendResult.SENT, InvitationSendResult.NOT_SENDABLE);
        } finally {
            pool.shutdownNow();
        }

        assertThat(calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'INVITATION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aRecipientAlreadySentIsNeverSentAgain() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);
        InvitationRecipientSendTransaction sender = senderWith(recordingGateway(), disabledBreaker());

        send(sender, recipientId);
        InvitationSendResult second = send(sender, recipientId);

        assertThat(second).isEqualTo(InvitationSendResult.NOT_SENDABLE);
        assertThat(sentEmails).hasSize(1);
    }

    @Test
    void theWiredUseCaseSendsThroughTheDefaultMockGateway() {
        long personId = importedStudent();
        long waveId = runningWave(10);
        long recipientId = recipientOf(waveId, personId);

        InvitationSendResult result = sendInvitationToRecipient.sendInvitationToRecipient(recipientId);

        assertThat(result).isEqualTo(InvitationSendResult.SENT);
        assertThat(jdbc.queryForObject("select status from notification_delivery where id = ?", String.class,
                Long.valueOf(recipientColumn(recipientId, "delivery_id")))).isEqualTo("SENT_SIMULATED");
    }
}
