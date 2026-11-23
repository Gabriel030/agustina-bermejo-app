package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveInvalidTransitionException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.usecase.CancelInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.CreateInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.GetInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ListInvitationWaveRecipientsUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ListInvitationWavesUseCase;
import com.red_escuela.backend.notification.invitation.usecase.PauseInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.ResumeInvitationWaveUseCase;
import com.red_escuela.backend.notification.invitation.usecase.StartInvitationWaveUseCase;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InvitationWaveIT extends AbstractIntegrationTest {

    @Autowired
    private CreateInvitationWaveUseCase createInvitationWave;
    @Autowired
    private StartInvitationWaveUseCase startInvitationWave;
    @Autowired
    private PauseInvitationWaveUseCase pauseInvitationWave;
    @Autowired
    private ResumeInvitationWaveUseCase resumeInvitationWave;
    @Autowired
    private CancelInvitationWaveUseCase cancelInvitationWave;
    @Autowired
    private GetInvitationWaveUseCase getInvitationWave;
    @Autowired
    private ListInvitationWavesUseCase listInvitationWaves;
    @Autowired
    private ListInvitationWaveRecipientsUseCase listInvitationWaveRecipients;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long directorId;
    private long batchId;
    private String suffix;

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
            return AudienceFreezeFixtures.person(entityManager, roleRepository, "wave-director-" + suffix, true, "DIRECTOR").getId();
        });
        batchId = jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at, version)
                values ('Lote ' || ?, 'COMMITTED', (select id from institution limit 1), ?, now(), 0) returning id
                """, Long.class, suffix, directorId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(directorId), null, List.of()));
    }

    @AfterEach
    void restore() {
        SecurityContextHolder.clearContext();
        cleanTables();
    }

    private void cleanTables() {
        jdbc.update("delete from invitation_wave_recipient");
        jdbc.update("delete from invitation_wave");
        jdbc.update("delete from import_created_entity where batch_id in (select id from import_batch where name like 'Lote %')");
        jdbc.update("delete from import_batch where name like 'Lote %'");
    }

    private CreateInvitationWaveCommand command() {
        return new CreateInvitationWaveCommand("Oleada " + suffix, 10, 15, 100, batchId);
    }

    private long importedStudent(boolean withEmail, String password) {
        long personId = transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            String unique = UUID.randomUUID().toString();
            Person person = new Person();
            person.setName("Importada");
            person.setLastname(suffix);
            person.setDni(unique.substring(0, 12));
            person.setUsername("imported." + unique);
            person.setEmail(withEmail ? "imported." + unique + "@example.com" : null);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.setPassword(password);
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

    private long draftWave() {
        return createInvitationWave.createInvitationWave(command()).id();
    }

    @Test
    void createsADraftWaveWithSnapshotOfEligibleAndExcludedRecipients() {
        long eligible = importedStudent(true, null);
        long withoutEmail = importedStudent(false, null);
        importedStudent(true, "already-activated-hash");

        InvitationWaveResponse response = createInvitationWave.createInvitationWave(command());

        assertThat(response.status()).isEqualTo(InvitationWaveStatus.DRAFT);
        assertThat(response.eligibleCount()).isEqualTo(1);
        assertThat(response.excludedTotal()).isEqualTo(1);
        assertThat(response.sentTotal()).isZero();
        assertThat(response.createdByPersonId()).isEqualTo(directorId);
        assertThat(response.importBatchId()).isEqualTo(batchId);
        assertThat(response.batchSize()).isEqualTo(10);
        assertThat(response.intervalMinutes()).isEqualTo(15);
        assertThat(response.dailyCap()).isEqualTo(100);
        assertThat(response.startedAt()).isNull();
        PageResponse<InvitationWaveRecipientEntry> all = listInvitationWaveRecipients.listInvitationWaveRecipients(response.id(), null, 0, 50);
        assertThat(all.items()).extracting(InvitationWaveRecipientEntry::personId).containsExactlyInAnyOrder(eligible, withoutEmail);
        InvitationWaveRecipientEntry pending = all.items().stream().filter(e -> e.personId() == eligible).findFirst().orElseThrow();
        InvitationWaveRecipientEntry excluded = all.items().stream().filter(e -> e.personId() == withoutEmail).findFirst().orElseThrow();
        assertThat(pending.status()).isEqualTo(InvitationRecipientStatus.PENDING);
        assertThat(pending.exclusionReason()).isNull();
        assertThat(excluded.status()).isEqualTo(InvitationRecipientStatus.EXCLUDED);
        assertThat(excluded.exclusionReason()).isEqualTo(InvitationExclusionReason.NO_EMAIL);
    }

    @Test
    void anAccountThatActivatedBeforeTheSnapshotDoesNotEnterTheWave() {
        long willActivate = importedStudent(true, null);
        long stays = importedStudent(true, null);
        jdbc.update("update person set password = 'hash' where id = ?", willActivate);

        long waveId = draftWave();

        PageResponse<InvitationWaveRecipientEntry> all = listInvitationWaveRecipients.listInvitationWaveRecipients(waveId, null, 0, 50);
        assertThat(all.items()).extracting(InvitationWaveRecipientEntry::personId).containsExactly(stays);
    }

    @Test
    void previewPagesEligibleAndExcludedSeparately() {
        for (int i = 0; i < 3; i++) {
            importedStudent(true, null);
        }
        importedStudent(false, null);
        long waveId = draftWave();

        PageResponse<InvitationWaveRecipientEntry> eligibleFirst = listInvitationWaveRecipients.listInvitationWaveRecipients(waveId, InvitationRecipientStatus.PENDING, 0, 2);
        PageResponse<InvitationWaveRecipientEntry> eligibleSecond = listInvitationWaveRecipients.listInvitationWaveRecipients(waveId, InvitationRecipientStatus.PENDING, 1, 2);
        PageResponse<InvitationWaveRecipientEntry> excluded = listInvitationWaveRecipients.listInvitationWaveRecipients(waveId, InvitationRecipientStatus.EXCLUDED, 0, 10);

        assertThat(eligibleFirst.totalElements()).isEqualTo(3);
        assertThat(eligibleFirst.items()).hasSize(2);
        assertThat(eligibleSecond.items()).hasSize(1);
        assertThat(excluded.items()).hasSize(1);
        assertThat(excluded.items().get(0).exclusionReason()).isEqualTo(InvitationExclusionReason.NO_EMAIL);
    }

    @Test
    void createsAnEmptyWaveWhenThereAreNoCandidates() {
        InvitationWaveResponse response = createInvitationWave.createInvitationWave(command());

        assertThat(response.eligibleCount()).isZero();
        assertThat(response.excludedTotal()).isZero();
    }

    @Test
    void invalidParametersAreRejectedAndNothingIsPersisted() {
        importedStudent(true, null);

        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 0, 15, 100, batchId)))
                .isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 0, 100, batchId)))
                .isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 15, 0, batchId)))
                .isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 15, 301, batchId)))
                .isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", null, 15, 100, batchId)))
                .isInstanceOf(RequestFieldRequiredException.class);
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave", Long.class)).isZero();
    }

    @Test
    void aDailyCapEqualToTheGlobalLimitIsAccepted() {
        InvitationWaveResponse response = createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 15, 300, batchId));

        assertThat(response.dailyCap()).isEqualTo(300);
    }

    @Test
    void walksThroughStartPauseResumeAndRecordsTimestamps() {
        long waveId = draftWave();

        InvitationWaveResponse started = startInvitationWave.startInvitationWave(waveId);
        InvitationWaveResponse paused = pauseInvitationWave.pauseInvitationWave(waveId);
        InvitationWaveResponse resumed = resumeInvitationWave.resumeInvitationWave(waveId);
        InvitationWaveResponse cancelled = cancelInvitationWave.cancelInvitationWave(waveId);

        assertThat(started.status()).isEqualTo(InvitationWaveStatus.RUNNING);
        assertThat(started.startedAt()).isNotNull();
        assertThat(paused.status()).isEqualTo(InvitationWaveStatus.PAUSED);
        assertThat(paused.pausedAt()).isNotNull();
        assertThat(resumed.status()).isEqualTo(InvitationWaveStatus.RUNNING);
        assertThat(cancelled.status()).isEqualTo(InvitationWaveStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select version from invitation_wave where id = ?", Long.class, waveId)).isEqualTo(4L);
    }

    @Test
    void invalidTransitionsAreRejectedAndLeaveTheWaveUntouched() {
        long waveId = draftWave();

        assertThatThrownBy(() -> pauseInvitationWave.pauseInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> resumeInvitationWave.resumeInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        startInvitationWave.startInvitationWave(waveId);
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> resumeInvitationWave.resumeInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        cancelInvitationWave.cancelInvitationWave(waveId);
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> cancelInvitationWave.cancelInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);

        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.CANCELLED);
    }

    @Test
    void aCompletedWaveCannotBeCancelled() {
        long waveId = draftWave();
        jdbc.update("update invitation_wave set status = 'COMPLETED' where id = ?", waveId);

        assertThatThrownBy(() -> cancelInvitationWave.cancelInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
    }

    @Test
    void unknownWaveIsReportedAsNotFound() {
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(-1L)).isInstanceOf(InvitationWaveNotFoundException.class);
        assertThatThrownBy(() -> getInvitationWave.getInvitationWave(-1L)).isInstanceOf(InvitationWaveNotFoundException.class);
        assertThatThrownBy(() -> listInvitationWaveRecipients.listInvitationWaveRecipients(-1L, null, 0, 10)).isInstanceOf(InvitationWaveNotFoundException.class);
    }

    @Test
    void listsWavesNewestFirst() {
        long first = draftWave();
        long second = draftWave();

        PageResponse<InvitationWaveResponse> page = listInvitationWaves.listInvitationWaves(0, 10);

        assertThat(page.items()).extracting(InvitationWaveResponse::id).containsExactly(second, first);
    }

    @Test
    void twoConcurrentStartsLetExactlyOneWin() throws Exception {
        long waveId = draftWave();

        List<Object> outcomes = runConcurrently(
                () -> startInvitationWave.startInvitationWave(waveId),
                () -> startInvitationWave.startInvitationWave(waveId));

        assertThat(outcomes).filteredOn(InvitationWaveResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(InvitationWaveInvalidTransitionException.class::isInstance).hasSize(1);
        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.RUNNING);
    }

    @Test
    void concurrentStartAndCancelEndCancelledWithoutLosingTheCancellation() throws Exception {
        long waveId = draftWave();

        List<Object> outcomes = runConcurrently(
                () -> startInvitationWave.startInvitationWave(waveId),
                () -> cancelInvitationWave.cancelInvitationWave(waveId));

        assertThat(outcomes).noneMatch(outcome -> outcome instanceof RuntimeException
                && !(outcome instanceof InvitationWaveInvalidTransitionException));
        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.CANCELLED);
    }

    @Test
    void aPersonCannotBeTwiceInTheSameWave() {
        long personId = importedStudent(true, null);
        long waveId = draftWave();

        assertThatThrownBy(() -> jdbc.update(
                "insert into invitation_wave_recipient (wave_id, person_id, status) values (?, ?, 'PENDING')", waveId, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePersonCanBeInDifferentWaves() {
        importedStudent(true, null);

        long first = draftWave();
        long second = draftWave();

        assertThat(jdbc.queryForObject("select count(*) from invitation_wave_recipient where wave_id in (?, ?)", Long.class, first, second)).isEqualTo(2);
    }

    @Test
    void anExcludedRecipientMustCarryAReasonAndAPendingOneMustNot() {
        long personId = importedStudent(true, null);
        long waveId = draftWave();
        jdbc.update("delete from invitation_wave_recipient where wave_id = ?", waveId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into invitation_wave_recipient (wave_id, person_id, status) values (?, ?, 'EXCLUDED')", waveId, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into invitation_wave_recipient (wave_id, person_id, status, exclusion_reason) values (?, ?, 'PENDING', 'NO_EMAIL')", waveId, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Object> runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    outcomes.add(exception.getCause());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
