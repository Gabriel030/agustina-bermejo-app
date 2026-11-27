package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.InvalidCurriculumPlanTransitionException;
import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ActivateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.CreateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ExpireCurriculumPlanUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.config.TestSubjectCodes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CurriculumPlanConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private ProgramSubjectService programSubjectService;
    @Autowired
    private CreateCurriculumPlanUseCase createCurriculumPlanUseCase;
    @Autowired
    private ActivateCurriculumPlanUseCase activateCurriculumPlanUseCase;
    @Autowired
    private ExpireCurriculumPlanUseCase expireCurriculumPlanUseCase;
    @Autowired
    private CurriculumPlanRepository curriculumPlanRepository;
    @Autowired
    private CurriculumPlanEditPolicy editPolicy;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long programId;
    private Long currentPlanId;
    private Long draftPlanId;

    private void givenProgramWithCurrentPlanAndDraftCopy() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Program program = new Program();
            program.setName("Tecnicatura Concurrente " + System.nanoTime());
            program.setTotalYears(3);
            entityManager.persist(program);
            Subject subject = new Subject();
            subject.setStatus(SubjectStatus.ACTIVE);
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia concurrente " + System.nanoTime());
            entityManager.persist(subject);
            entityManager.flush();
            programId = program.getId();
            currentPlanId = programSubjectService.addSubject(programId, new CreateProgramSubjectRequest(
                    subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null))
                    .getPlan().getId();
        });
        draftPlanId = createCurriculumPlanUseCase.createDraftVersion(programId,
                new CreateCurriculumPlanRequest("Plan siguiente", LocalDate.of(2028, 3, 1), null, null, null, currentPlanId)).id();
    }

    @RepeatedTest(3)
    void concurrentDoubleActivationOfTheSameDraftActivatesItOnceAndRejectsTheOther() throws Exception {
        givenProgramWithCurrentPlanAndDraftCopy();
        Callable<Object> activate = () -> activateCurriculumPlanUseCase.activate(draftPlanId);

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(activate, activate));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement()
                .satisfies(outcome -> assertThat(outcome.failure()).isInstanceOf(InvalidCurriculumPlanTransitionException.class));
        assertThat(curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(programId))
                .extracting(CurriculumPlan::getStatus)
                .containsExactly(CurriculumPlanStatus.EXPIRED, CurriculumPlanStatus.ACTIVE);
    }

    @RepeatedTest(3)
    void activatingTheDraftWhileExpiringTheCurrentPlanLeavesASingleActivePlan() throws Exception {
        givenProgramWithCurrentPlanAndDraftCopy();
        Callable<Object> activateDraft = () -> activateCurriculumPlanUseCase.activate(draftPlanId);
        Callable<Object> expireCurrent = () -> expireCurriculumPlanUseCase.expire(currentPlanId,
                new ExpireCurriculumPlanRequest(LocalDate.now(), null));

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(activateDraft, expireCurrent));

        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.failure()).isInstanceOf(InvalidCurriculumPlanTransitionException.class));
        List<CurriculumPlan> plans = curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(programId);
        assertThat(plans).filteredOn(plan -> plan.getStatus() == CurriculumPlanStatus.ACTIVE).hasSize(1);
        assertThat(plans.get(0).getStatus()).isEqualTo(CurriculumPlanStatus.EXPIRED);
    }

    @Test
    void concurrentDraftCreationAssignsDifferentVersionNumbersWithoutFailing() throws Exception {
        givenProgramWithCurrentPlanAndDraftCopy();
        Callable<CurriculumPlanResponse> createDraft = () -> createCurriculumPlanUseCase.createDraftVersion(programId,
                new CreateCurriculumPlanRequest("Plan paralelo", LocalDate.of(2029, 3, 1), null, null, null, null));

        List<Outcome<CurriculumPlanResponse>> outcomes = ConcurrentRace.run(List.of(createDraft, createDraft));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(programId))
                .extracting(CurriculumPlan::getVersionNumber).containsExactly(1, 2, 3, 4);
    }

    @Test
    void structureEditWaitsForATransactionHoldingThePlanLockAsEnrollmentWillDo() throws Exception {
        givenProgramWithCurrentPlanAndDraftCopy();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> enrollment = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                curriculumPlanRepository.findByIdForUpdate(currentPlanId);
                lockHeld.countDown();
                awaitQuietly(release);
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> edit = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> editPolicy.lockEditableStructureOf(currentPlanId)));

            assertThatThrownBy(() -> edit.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            enrollment.get(10, TimeUnit.SECONDS);
            edit.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
