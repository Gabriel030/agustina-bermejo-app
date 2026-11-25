package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.identity.api.GuardianEmailCollisionFinder;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.specialist.SharedDailyEmailQuota;
import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.notification.invitation.specialist.ActivationInvitationEmailFactory;
import com.red_escuela.backend.notification.invitation.specialist.InvitationActivationTokenRequester;
import com.red_escuela.backend.notification.invitation.specialist.InvitationDeliveryRecorder;
import com.red_escuela.backend.notification.invitation.specialist.InvitationMailSender;
import com.red_escuela.backend.notification.invitation.specialist.InvitationRecipientSendTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationSendingCapacity;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveSettings;
import com.red_escuela.backend.notification.invitation.usecase.CreateInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.StartInvitationWaveUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.mail.EmailDeliveryReceipt;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadSettings;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerSettings;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class SharedDailyQuotaIT extends AbstractBroadcastDispatchIT {

    private static final int DAILY_LIMIT = 4;

    @Autowired
    private CreateInvitationWaveUseCase createInvitationWave;
    @Autowired
    private StartInvitationWaveUseCase startInvitationWave;
    @Autowired
    private InvitationWaveRepository waveRepository;
    @Autowired
    private InvitationWaveRecipientRepository recipientRepository;
    @Autowired
    private PersonContactFinder personContactFinder;
    @Autowired
    private GuardianEmailCollisionFinder guardianEmailCollisionFinder;
    @Autowired
    private InvitationActivationTokenRequester tokenRequester;
    @Autowired
    private ActivationInvitationEmailFactory emailFactory;
    @Autowired
    private InvitationDeliveryRecorder deliveryRecorder;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long directorId;
    private long batchId;
    private String suffix;

    @BeforeEach
    void prepareInvitationFixtures() {
        cleanInvitationTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        directorId = transactionTemplate.execute(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            return AudienceFreezeFixtures.person(entityManager, roleRepository, "quota-director-" + suffix, true, "DIRECTOR").getId();
        });
        batchId = jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at, version)
                values ('Cupo ' || ?, 'COMMITTED', (select id from institution limit 1), ?, now(), 0) returning id
                """, Long.class, suffix, directorId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(directorId), null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        cleanInvitationTables();
    }

    private void cleanInvitationTables() {
        jdbc.update("delete from invitation_wave_recipient");
        jdbc.update("delete from invitation_wave");
        jdbc.update("delete from notification_delivery");
        jdbc.update("delete from activation_token where person_id in (select id from person where last_name like 'Cupo-%')");
        jdbc.update("delete from import_created_entity where batch_id in (select id from import_batch where name like 'Cupo %')");
        jdbc.update("delete from import_batch where name like 'Cupo %'");
    }

    private long importedStudent() {
        long personId = transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            String unique = UUID.randomUUID().toString();
            Person person = new Person();
            person.setName("Importada");
            person.setLastname("Cupo-" + suffix);
            person.setDni(unique.substring(0, 12));
            person.setUsername("cupo." + unique);
            person.setEmail("cupo." + unique + "@example.com");
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

    private long runningWaveWithOneRecipient() {
        importedStudent();
        long waveId = createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada " + suffix, 1, 15, 50, batchId)).id();
        startInvitationWave.startInvitationWave(waveId, directorId);
        return jdbc.queryForObject("select id from invitation_wave_recipient where wave_id = ?", Long.class, waveId);
    }

    private TransactionalEmailGateway gatewayThatWaitsForTheOtherSender(CyclicBarrier bothAreAboutToSend) {
        return email -> {
            waitForTheOtherSender(bothAreAboutToSend);
            return new EmailDeliveryReceipt("provider-" + UUID.randomUUID().toString().substring(0, 8), false);
        };
    }

    private boolean waitForTheOtherSender(CyclicBarrier bothAreAboutToSend) {
        try {
            bothAreAboutToSend.await(1, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException | BrokenBarrierException otherSenderNeverArrived) {
            return false;
        }
    }

    private InvitationRecipientSendTransaction invitationSenderWith(TransactionalEmailGateway gateway) {
        InvitationSendingCapacity capacity = new InvitationSendingCapacity(deliveryRepository, sharedQuota(),
                new InvitationWaveSettings(DAILY_LIMIT, 5000, 3), MutableClock.INSTANCE);
        CircuitBreaker breaker = new CircuitBreaker("mail", new CircuitBreakerSettings(false, 2, 1, Duration.ofMinutes(1)),
                MutableClock.INSTANCE, new SimpleMeterRegistry());
        Bulkhead bulkhead = new Bulkhead("mail", new BulkheadSettings(false, 4, Duration.ZERO), new SimpleMeterRegistry());
        return new InvitationRecipientSendTransaction(waveRepository, recipientRepository, deliveryRepository, personContactFinder,
                guardianEmailCollisionFinder, tokenRequester, emailFactory, new InvitationMailSender(gateway, breaker, bulkhead),
                deliveryRecorder, capacity, MutableClock.INSTANCE);
    }

    private SharedDailyEmailQuota sharedQuota() {
        return new SharedDailyEmailQuota(deliveryRepository, MutableClock.INSTANCE);
    }

    private BroadcastEmailDispatchJob broadcastJobSharingTheQuota(EmailDeliveryChannel channel) {
        BroadcastDispatchSettings settings = new BroadcastDispatchSettings(DAILY_LIMIT, 0, 100, "09:00", "20:00");
        return new BroadcastEmailDispatchJob(deliveryRepository, channel, consentRecheck, progressTracker, settings, sharedQuota(),
                transactionManager, MutableClock.INSTANCE, new SimpleMeterRegistry());
    }

    private long sentToday(LocalDate day) {
        return sentOn(day, DeliveryCategory.BROADCAST) + sentOn(day, DeliveryCategory.INVITATION) + sentOn(day, DeliveryCategory.ANNOUNCEMENT);
    }

    @Test
    void invitationsAndBroadcastsNeverExceedTheLimit() throws Exception {
        LocalDate day = LocalDate.of(2043, 6, 10);
        queueBroadcasts(persistPerson(), 5, day);
        at(day, 8, 0);
        sendTransactional(persistPerson(), DeliveryCategory.ANNOUNCEMENT, DAILY_LIMIT - 1, day);
        long recipientId = runningWaveWithOneRecipient();
        CyclicBarrier bothAreAboutToSend = new CyclicBarrier(2);
        BroadcastEmailDispatchJob broadcastJob = broadcastJobSharingTheQuota(recordingChannel(gatewayThatWaitsForTheOtherSender(bothAreAboutToSend)));
        InvitationRecipientSendTransaction invitationSender = invitationSenderWith(gatewayThatWaitsForTheOtherSender(bothAreAboutToSend));
        at(day, 9, 0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> broadcasts = executor.submit(broadcastJob::dispatchPendingBroadcastEmails);
            Future<InvitationSendResult> invitation = executor.submit(() -> {
                try {
                    return transactionTemplate.execute(status -> invitationSender.sendInvitationToRecipient(recipientId));
                } catch (InvitationSendDeferredException failure) {
                    return InvitationSendResult.DEFERRED;
                }
            });
            broadcasts.get(30, TimeUnit.SECONDS);
            invitation.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(sentToday(day)).isEqualTo(DAILY_LIMIT);
    }
}
