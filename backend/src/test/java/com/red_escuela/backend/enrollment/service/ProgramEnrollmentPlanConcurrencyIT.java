package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentLock;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class ProgramEnrollmentPlanConcurrencyIT extends AbstractIntegrationTest {
    @Autowired
    private CurriculumPlanEnrollmentLock curriculumPlanEnrollmentLock;
    @Autowired
    private CurriculumPlanRepository curriculumPlanRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Long programId;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        if (programId != null) {
            jdbc.update("delete from curriculum_plan where program_id = ?", programId);
            jdbc.update("delete from program where id = ?", programId);
        }
    }

    private Long commitProgramWithActivePlan() {
        TransactionTemplate commit = new TransactionTemplate(transactionManager);
        return commit.execute(status -> {
            Program program = new Program();
            program.setName("Carrera concurrente " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
            programId = program.getId();
            CurriculumPlan plan = CurriculumPlan.initialVersionOf(program, java.time.LocalDate.of(2020, 1, 1));
            entityManager.persist(plan);
            return plan.getId();
        });
    }

    @Test
    void enrollmentHoldsASharedLockOnThePlanSoAConcurrentStructureEditWaitsForTheCommit() {
        Long planId = commitProgramWithActivePlan();
        CountDownLatch enrollmentHoldsLock = new CountDownLatch(1);
        CountDownLatch commitEnrollment = new CountDownLatch(1);
        TransactionTemplate enrollmentTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate editTransaction = new TransactionTemplate(transactionManager);

        CompletableFuture<Void> enrollment = CompletableFuture.runAsync(() -> enrollmentTransaction.executeWithoutResult(status -> {
            curriculumPlanEnrollmentLock.lockOpenForNewStudents(planId);
            enrollmentHoldsLock.countDown();
            awaitUnchecked(commitEnrollment);
        }), executor);
        awaitUnchecked(enrollmentHoldsLock);

        CompletableFuture<Void> edit = CompletableFuture.runAsync(() -> editTransaction.executeWithoutResult(
                status -> curriculumPlanRepository.findByIdForUpdate(planId).orElseThrow()), executor);

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> !edit.isDone());
        commitEnrollment.countDown();
        await().atMost(Duration.ofSeconds(10)).until(edit::isDone);

        assertThat(enrollment).isCompleted();
        assertThat(edit).isCompleted();
    }

    @Test
    void twoEnrollmentsShareThePlanLockWithoutBlockingEachOther() {
        Long planId = commitProgramWithActivePlan();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate firstTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate secondTransaction = new TransactionTemplate(transactionManager);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> firstTransaction.executeWithoutResult(status -> {
            curriculumPlanEnrollmentLock.lockOpenForNewStudents(planId);
            firstHoldsLock.countDown();
            awaitUnchecked(commitFirst);
        }), executor);
        awaitUnchecked(firstHoldsLock);

        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> secondTransaction.executeWithoutResult(
                status -> curriculumPlanEnrollmentLock.lockOpenForNewStudents(planId)), executor);

        await().atMost(Duration.ofSeconds(5)).until(second::isDone);
        commitFirst.countDown();
        await().atMost(Duration.ofSeconds(5)).until(first::isDone);
        assertThat(second).isCompleted();
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
