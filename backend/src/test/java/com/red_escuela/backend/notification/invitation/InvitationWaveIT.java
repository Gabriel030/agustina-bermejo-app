package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.notification.invitation.domain.InvitationErrorCode;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveParameters;
import com.red_escuela.backend.notification.invitation.exception.InvitationPersonAlreadyInvitedException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveHasOpenRecipientsException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveImportBatchNotFoundException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveWithoutRecipientsException;
import com.red_escuela.backend.notification.invitation.specialist.CreateInvitationWaveTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
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
    private InvitationWaveLifecycleTransaction lifecycleTransaction;
    @Autowired
    private CreateInvitationWaveTransaction createTransaction;
    @Autowired
    private InvitationWaveRecipientRepository recipientRepository;
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

    private long queuedPersonId;

    private long releasableWave() {
        long pending = importedStudent(true, null);
        queuedPersonId = importedStudent(true, null);
        long failed = importedStudent(true, null);
        long sent = importedStudent(true, null);
        long waveId = draftWave();
        jdbc.update("update invitation_wave_recipient set status = 'QUEUED', delivery_id = 1, attempts = 1 where wave_id = ? and person_id = ?", waveId, queuedPersonId);
        jdbc.update("update invitation_wave_recipient set status = 'FAILED', delivery_id = 2, attempts = 1, last_error_code = 'UNKNOWN' where wave_id = ? and person_id = ?", waveId, failed);
        jdbc.update("update invitation_wave_recipient set status = 'SENT', delivery_id = 3, attempts = 1, sent_at = now() where wave_id = ? and person_id = ?", waveId, sent);
        assertThat(pending).isPositive();
        return waveId;
    }

    private long startableWave() {
        importedStudent(true, null);
        return draftWave();
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
        long waveId = startableWave();

        InvitationWaveResponse started = startInvitationWave.startInvitationWave(waveId, directorId);
        InvitationWaveResponse paused = pauseInvitationWave.pauseInvitationWave(waveId, directorId);
        InvitationWaveResponse resumed = resumeInvitationWave.resumeInvitationWave(waveId, directorId);
        InvitationWaveResponse cancelled = cancelInvitationWave.cancelInvitationWave(waveId, directorId);

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
        long waveId = startableWave();

        assertThatThrownBy(() -> pauseInvitationWave.pauseInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> resumeInvitationWave.resumeInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        startInvitationWave.startInvitationWave(waveId, directorId);
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> resumeInvitationWave.resumeInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        cancelInvitationWave.cancelInvitationWave(waveId, directorId);
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
        assertThatThrownBy(() -> cancelInvitationWave.cancelInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);

        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.CANCELLED);
    }

    @Test
    void aCompletedWaveCannotBeCancelled() {
        long waveId = draftWave();
        jdbc.update("update invitation_wave set status = 'COMPLETED', started_at = now(), completed_at = now() where id = ?", waveId);

        assertThatThrownBy(() -> cancelInvitationWave.cancelInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
    }

    @Test
    void unknownWaveIsReportedAsNotFound() {
        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(-1L, directorId)).isInstanceOf(InvitationWaveNotFoundException.class);
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
        long waveId = startableWave();

        List<Object> outcomes = runConcurrently(
                () -> startInvitationWave.startInvitationWave(waveId, directorId),
                () -> startInvitationWave.startInvitationWave(waveId, directorId));

        assertThat(outcomes).filteredOn(InvitationWaveResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(InvitationWaveInvalidTransitionException.class::isInstance).hasSize(1);
        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.RUNNING);
    }

    @Test
    void concurrentStartAndCancelEndCancelledWithoutLosingTheCancellation() throws Exception {
        long waveId = startableWave();

        List<Object> outcomes = runConcurrently(
                () -> startInvitationWave.startInvitationWave(waveId, directorId),
                () -> cancelInvitationWave.cancelInvitationWave(waveId, directorId));

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
    void aPersonPendingInAnotherWaveIsExcludedFromTheNewOne() {
        long personId = importedStudent(true, null);
        long first = draftWave();

        long second = draftWave();

        InvitationWaveRecipientEntry entry = listInvitationWaveRecipients.listInvitationWaveRecipients(second, null, 0, 10).items().get(0);
        assertThat(entry.personId()).isEqualTo(personId);
        assertThat(entry.status()).isEqualTo(InvitationRecipientStatus.EXCLUDED);
        assertThat(entry.exclusionReason()).isEqualTo(InvitationExclusionReason.IN_OTHER_WAVE);
        assertThat(getInvitationWave.getInvitationWave(first).eligibleCount()).isEqualTo(1);
        assertThat(getInvitationWave.getInvitationWave(second).excludedTotal()).isEqualTo(1);
    }

    @Test
    void aFailedRecipientStillBlocksTheNewWaveButSentActivatedAndExcludedDoNot() {
        long failedPerson = importedStudent(true, null);
        long sentPerson = importedStudent(true, null);
        long activatedPerson = importedStudent(true, null);
        long first = draftWave();
        jdbc.update("update invitation_wave_recipient set status = 'FAILED' where wave_id = ? and person_id = ?", first, failedPerson);
        jdbc.update("update invitation_wave_recipient set status = 'SENT', delivery_id = 1, sent_at = now() where wave_id = ? and person_id = ?", first, sentPerson);
        jdbc.update("update invitation_wave_recipient set status = 'ACTIVATED' where wave_id = ? and person_id = ?", first, activatedPerson);

        long second = draftWave();

        List<InvitationWaveRecipientEntry> entries = listInvitationWaveRecipients.listInvitationWaveRecipients(second, null, 0, 10).items();
        assertThat(entries).filteredOn(entry -> entry.personId() == failedPerson)
                .singleElement().satisfies(entry -> assertThat(entry.exclusionReason()).isEqualTo(InvitationExclusionReason.IN_OTHER_WAVE));
        assertThat(entries).filteredOn(entry -> entry.personId() == sentPerson || entry.personId() == activatedPerson)
                .hasSize(2).allMatch(entry -> entry.status() == InvitationRecipientStatus.PENDING);
    }

    @Test
    void theDatabaseRejectsASecondOpenInvitationForTheSamePerson() {
        long other = draftWave();
        long personId = importedStudent(true, null);
        draftWave();

        assertThatThrownBy(() -> jdbc.update(
                "insert into invitation_wave_recipient (wave_id, person_id, status) values (?, ?, 'PENDING')", other, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnotherOpenInvitationWhileTheEarlierOneIsFailed() {
        long other = draftWave();
        long personId = importedStudent(true, null);
        long first = draftWave();
        jdbc.update("update invitation_wave_recipient set status = 'FAILED' where wave_id = ?", first);

        assertThatThrownBy(() -> jdbc.update(
                "insert into invitation_wave_recipient (wave_id, person_id, status) values (?, ?, 'PENDING')", other, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update invitation_wave_recipient set status = 'SENT', delivery_id = 1, sent_at = now() where wave_id = ?", first);
        assertThat(jdbc.update("insert into invitation_wave_recipient (wave_id, person_id, status) values (?, ?, 'PENDING')", other, personId)).isEqualTo(1);
    }

    @Test
    void retryingAFailedRecipientNeverCollidesWithTheIndex() {
        importedStudent(true, null);
        long waveId = draftWave();
        Long recipientId = jdbc.queryForObject("select id from invitation_wave_recipient where wave_id = ?", Long.class, waveId);

        transactions.executeWithoutResult(status -> {
            InvitationWaveRecipient recipient = recipientRepository.findById(recipientId).orElseThrow();
            recipient.queue(1L);
            recipient.markFailed(InvitationErrorCode.PROVIDER_UNAVAILABLE);
            recipientRepository.saveAndFlush(recipient);
        });
        transactions.executeWithoutResult(status -> {
            InvitationWaveRecipient recipient = recipientRepository.findById(recipientId).orElseThrow();
            recipient.queue(2L);
            recipientRepository.saveAndFlush(recipient);
        });

        assertThat(jdbc.queryForObject("select status from invitation_wave_recipient where id = ?", String.class, recipientId)).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("select attempts from invitation_wave_recipient where id = ?", Integer.class, recipientId)).isEqualTo(2);
    }

    @Test
    void concurrentCreationsNeverLeaveAPersonOpenInTwoWaves() throws Exception {
        long personId = importedStudent(true, null);
        List<ImportedAccount> candidates = List.of(new ImportedAccount(personId, "a@example.com", "Importada", batchId));
        InvitationWaveParameters parameters = new InvitationWaveParameters("Concurrente", 10, 15, 100);

        List<Object> outcomes = runConcurrently(
                () -> createTransaction.createInvitationWave(parameters, batchId, directorId, candidates),
                () -> createTransaction.createInvitationWave(parameters, batchId, directorId, candidates));

        assertThat(outcomes).allMatch(outcome -> outcome instanceof InvitationWaveResponse
                || outcome instanceof InvitationPersonAlreadyInvitedException);
        List<InvitationWaveResponse> created = outcomes.stream().filter(InvitationWaveResponse.class::isInstance)
                .map(InvitationWaveResponse.class::cast).toList();
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave", Long.class)).isEqualTo((long) created.size());
        assertThat(created).filteredOn(wave -> wave.eligibleCount() == 1).hasSize(1);
        assertThat(created).filteredOn(wave -> wave.eligibleCount() == 0).allMatch(wave -> wave.excludedTotal() == 1);
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave_recipient where person_id = ? and status in ('PENDING', 'QUEUED', 'FAILED')", Long.class, personId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave_recipient where person_id = ? and exclusion_reason = 'IN_OTHER_WAVE'", Long.class, personId))
                .isEqualTo((long) created.size() - 1);
    }

    @Test
    void cancellingARunningWaveReleasesPendingQueuedAndFailedRecipients() {
        long waveId = releasableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);

        InvitationWaveResponse cancelled = cancelInvitationWave.cancelInvitationWave(waveId, directorId);

        assertThat(cancelled.status()).isEqualTo(InvitationWaveStatus.CANCELLED);
        assertThat(cancelled.eligibleCount()).isZero();
        assertThat(cancelled.queuedTotal()).isZero();
        assertThat(cancelled.failedTotal()).isZero();
        assertThat(cancelled.sentTotal()).isEqualTo(1);
        assertThat(cancelled.excludedTotal()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave_recipient where wave_id = ? and status = 'EXCLUDED' and exclusion_reason = 'WAVE_CANCELLED'", Long.class, waveId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select delivery_id from invitation_wave_recipient where wave_id = ? and person_id = ?", Long.class, waveId, queuedPersonId)).isEqualTo(1L);
    }

    @Test
    void cancellingAPausedWaveReleasesItsOpenRecipientsAndThePersonCanJoinAnotherWave() {
        long waveId = releasableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);
        pauseInvitationWave.pauseInvitationWave(waveId, directorId);

        cancelInvitationWave.cancelInvitationWave(waveId, directorId);

        long next = draftWave();
        InvitationWaveResponse nextWave = getInvitationWave.getInvitationWave(next);
        assertThat(nextWave.eligibleCount()).isEqualTo(4);
        assertThat(nextWave.excludedTotal()).isZero();
    }

    @Test
    void cancellingADraftWaveReleasesItsPendingRecipients() {
        importedStudent(true, null);
        long waveId = draftWave();

        cancelInvitationWave.cancelInvitationWave(waveId, directorId);

        long next = draftWave();
        assertThat(getInvitationWave.getInvitationWave(next).eligibleCount()).isEqualTo(1);
    }

    @Test
    void resumeRecordsTheActorAndTimestamp() {
        long waveId = startableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);
        pauseInvitationWave.pauseInvitationWave(waveId, directorId);

        InvitationWaveResponse resumed = resumeInvitationWave.resumeInvitationWave(waveId, directorId);

        assertThat(resumed.resumedByPersonId()).isEqualTo(directorId);
        assertThat(resumed.resumedAt()).isNotNull();
    }

    @Test
    void domainTransitionsProduceRowsThatSatisfyTheRecipientChecks() {
        importedStudent(true, null);
        long waveId = draftWave();
        Long recipientId = jdbc.queryForObject("select id from invitation_wave_recipient where wave_id = ?", Long.class, waveId);

        for (InvitationErrorCode code : InvitationErrorCode.values()) {
            transactions.executeWithoutResult(status -> {
                InvitationWaveRecipient recipient = recipientRepository.findById(recipientId).orElseThrow();
                recipient.queue(1L);
                recipient.markFailed(code);
                recipientRepository.saveAndFlush(recipient);
            });
        }
        transactions.executeWithoutResult(status -> {
            InvitationWaveRecipient recipient = recipientRepository.findById(recipientId).orElseThrow();
            recipient.queue(5L);
            recipient.markSent(java.time.Instant.now());
            recipientRepository.saveAndFlush(recipient);
        });
        assertThat(jdbc.queryForObject("select status from invitation_wave_recipient where id = ?", String.class, recipientId)).isEqualTo("SENT");
        transactions.executeWithoutResult(status -> {
            InvitationWaveRecipient recipient = recipientRepository.findById(recipientId).orElseThrow();
            recipient.markActivated();
            recipientRepository.saveAndFlush(recipient);
        });
        assertThat(jdbc.queryForObject("select status from invitation_wave_recipient where id = ?", String.class, recipientId)).isEqualTo("ACTIVATED");
    }

    @Test
    void theDatabaseRejectsRecipientRowsThatBypassTheDomainMethods() {
        long personId = importedStudent(true, null);
        long waveId = draftWave();
        jdbc.update("delete from invitation_wave_recipient where wave_id = ?", waveId);

        assertThatThrownBy(() -> recipientRepository.saveAndFlush(InvitationWaveRecipient.builder().waveId(waveId).personId(personId)
                .status(InvitationRecipientStatus.QUEUED).build())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recipientRepository.saveAndFlush(InvitationWaveRecipient.builder().waveId(waveId).personId(personId)
                .status(InvitationRecipientStatus.SENT).deliveryId(1L).build())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> recipientRepository.saveAndFlush(InvitationWaveRecipient.builder().waveId(waveId).personId(personId)
                .status(InvitationRecipientStatus.EXCLUDED).build())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into invitation_wave_recipient (wave_id, person_id, status, last_error_code) values (?, ?, 'FAILED', 'free text')", waveId, personId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void importBatchIsRequiredAndMustExist() {
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 15, 100, null)))
                .isInstanceOf(RequestFieldRequiredException.class);
        assertThatThrownBy(() -> createInvitationWave.createInvitationWave(new CreateInvitationWaveCommand("Oleada", 10, 15, 100, -5L)))
                .isInstanceOf(InvitationWaveImportBatchNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from invitation_wave", Long.class)).isZero();
    }

    @Test
    void theDatabaseRejectsAWaveOfAnUnknownImportBatch() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into invitation_wave (name, status, batch_size, interval_minutes, daily_cap, import_batch_id, created_by_person_id, created_at)
                values ('X', 'DRAFT', 1, 1, 1, -5, ?, now())
                """, directorId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void startingAWaveWithoutPendingRecipientsFailsAndLeavesItInDraft() {
        importedStudent(false, null);
        long waveId = draftWave();

        assertThatThrownBy(() -> startInvitationWave.startInvitationWave(waveId, directorId)).isInstanceOf(InvitationWaveWithoutRecipientsException.class);

        InvitationWaveResponse wave = getInvitationWave.getInvitationWave(waveId);
        assertThat(wave.status()).isEqualTo(InvitationWaveStatus.DRAFT);
        assertThat(wave.startedAt()).isNull();
        assertThat(wave.startedByPersonId()).isNull();
    }

    @Test
    void startRecordsTheActorAndPauseAndCancelToo() {
        long waveId = startableWave();

        InvitationWaveResponse started = startInvitationWave.startInvitationWave(waveId, directorId);
        InvitationWaveResponse paused = pauseInvitationWave.pauseInvitationWave(waveId, directorId);
        InvitationWaveResponse cancelled = cancelInvitationWave.cancelInvitationWave(waveId, directorId);

        assertThat(started.startedByPersonId()).isEqualTo(directorId);
        assertThat(paused.pausedByPersonId()).isEqualTo(directorId);
        assertThat(cancelled.cancelledByPersonId()).isEqualTo(directorId);
        assertThat(cancelled.cancelledAt()).isNotNull();
    }

    @Test
    void completingWithPendingOrQueuedRecipientsIsRejected() {
        long waveId = startableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);

        assertThatThrownBy(() -> lifecycleTransaction.completeInvitationWave(waveId)).isInstanceOf(InvitationWaveHasOpenRecipientsException.class);

        assertThat(getInvitationWave.getInvitationWave(waveId).status()).isEqualTo(InvitationWaveStatus.RUNNING);
        jdbc.update("update invitation_wave_recipient set status = 'QUEUED', delivery_id = 1 where wave_id = ?", waveId);
        assertThatThrownBy(() -> lifecycleTransaction.completeInvitationWave(waveId)).isInstanceOf(InvitationWaveHasOpenRecipientsException.class);
    }

    @Test
    void completingWithoutOpenRecipientsRecordsTheCompletion() {
        long waveId = startableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);
        jdbc.update("update invitation_wave_recipient set status = 'ACTIVATED' where wave_id = ?", waveId);

        InvitationWaveResponse completed = lifecycleTransaction.completeInvitationWave(waveId);

        assertThat(completed.status()).isEqualTo(InvitationWaveStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.activatedTotal()).isEqualTo(1);
        assertThat(completed.eligibleCount()).isZero();
    }

    @Test
    void aPausedWaveCannotBeCompleted() {
        long waveId = startableWave();
        startInvitationWave.startInvitationWave(waveId, directorId);
        pauseInvitationWave.pauseInvitationWave(waveId, directorId);
        jdbc.update("update invitation_wave_recipient set status = 'ACTIVATED' where wave_id = ?", waveId);

        assertThatThrownBy(() -> lifecycleTransaction.completeInvitationWave(waveId)).isInstanceOf(InvitationWaveInvalidTransitionException.class);
    }

    @Test
    void totalsAreDerivedFromTheRecipientStatuses() {
        importedStudent(true, null);
        importedStudent(true, null);
        importedStudent(false, null);
        long waveId = draftWave();
        jdbc.update("update invitation_wave_recipient set status = 'FAILED' where id = (select min(id) from invitation_wave_recipient where wave_id = ? and status = 'PENDING')", waveId);

        InvitationWaveResponse wave = getInvitationWave.getInvitationWave(waveId);

        assertThat(wave.eligibleCount()).isEqualTo(1);
        assertThat(wave.failedTotal()).isEqualTo(1);
        assertThat(wave.excludedTotal()).isEqualTo(1);
        assertThat(wave.sentTotal()).isZero();
    }

    @Test
    void theDatabaseChecksTheWaveEnumsAndInvariants() {
        String insert = "insert into invitation_wave (name, status, batch_size, interval_minutes, daily_cap, import_batch_id, created_by_person_id, created_at, started_at, completed_at) values ('X', ?, ?, ?, ?, ?, ?, now(), ?, ?)";
        Object now = new java.sql.Timestamp(System.currentTimeMillis());

        assertThatThrownBy(() -> jdbc.update(insert, "BOGUS", 1, 1, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "DRAFT", 0, 1, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "DRAFT", 2, 1, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "DRAFT", 1, 0, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "DRAFT", 1, 1441, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "RUNNING", 1, 1, 1, batchId, directorId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "COMPLETED", 1, 1, 1, batchId, directorId, now, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "DRAFT", 1, 1, 1, batchId, directorId, null, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insert, "COMPLETED", 1, 1440, 1, batchId, directorId, now, now)).isEqualTo(1);
    }

    @Test
    void theDatabaseChecksTheRecipientEnumsAndInvariants() {
        long personId = importedStudent(true, null);
        long waveId = draftWave();
        jdbc.update("delete from invitation_wave_recipient where wave_id = ?", waveId);
        String insert = "insert into invitation_wave_recipient (wave_id, person_id, status, exclusion_reason, delivery_id, sent_at) values (?, ?, ?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, waveId, personId, "BOGUS", null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, waveId, personId, "EXCLUDED", "BOGUS", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, waveId, personId, "QUEUED", null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, waveId, personId, "SENT", null, 1L, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insert, waveId, personId, "EXCLUDED", "IN_OTHER_WAVE", null, null)).isEqualTo(1);
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
