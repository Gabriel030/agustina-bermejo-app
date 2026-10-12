package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyConflictException;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordCleanupJob;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IdempotencyKeyHandlerIT.IdempotentTestOperationConfiguration.class)
class IdempotencyKeyHandlerIT extends AbstractIntegrationTest {

    record TestCommand(String idempotencyKey, BigDecimal amount, StatusChangeActor actor) implements IdempotencyKeyed {
        TestCommand(String idempotencyKey, BigDecimal amount) {
            this(idempotencyKey, amount, StatusChangeActor.ofPerson(1L));
        }
    }

    record TestResult(String reference, BigDecimal amount) {
    }

    static final AtomicInteger EXECUTIONS = new AtomicInteger();
    static final AtomicBoolean FAIL_NEXT_EXECUTION = new AtomicBoolean();
    static volatile CountDownLatch processingEntered = new CountDownLatch(0);
    static volatile CountDownLatch processingMayContinue = new CountDownLatch(0);

    static class IdempotentTestOperation implements BillingOperation<TestCommand, TestResult> {
        @Override
        public BillingOperationType type() {
            return BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS;
        }

        @Override
        public Class<TestCommand> contextType() {
            return TestCommand.class;
        }

        @Override
        public List<String> validationMoments() {
            return List.of();
        }

        @Override
        public List<Class<? extends DomainEvent>> publishedEventTypes() {
            return List.of();
        }

        @Override
        public void requireValidContext(TestCommand context) {
        }

        @Override
        public TestResult processOperation(TestCommand context) {
            int execution = EXECUTIONS.incrementAndGet();
            processingEntered.countDown();
            awaitUninterrupted(processingMayContinue);
            if (FAIL_NEXT_EXECUTION.getAndSet(false)) {
                throw new IllegalStateException("the operation failed after claiming its key");
            }
            return new TestResult("payment-" + execution, context.amount());
        }

        @Override
        public List<? extends DomainEvent> domainEventsOf(TestCommand context, TestResult result) {
            return List.of();
        }

        private static void awaitUninterrupted(CountDownLatch latch) {
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test never released the processing");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration
    static class IdempotentTestOperationConfiguration {
        @Bean
        OperationPreHandlers idempotentTestPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper) {
            return new OperationPreHandlers(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS,
                    List.of(new IdempotencyKeyHandler<TestCommand>(recordService, objectMapper, TestResult.class)));
        }
    }

    @Autowired
    private BillingOperationPipeline pipeline;
    private final BillingOperation<TestCommand, TestResult> operation = new IdempotentTestOperation();
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IdempotencyRecordService recordService;
    @Autowired
    private IdempotencyRecordCleanupJob cleanupJob;
    @Autowired
    private LockProvider lockProvider;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void cleanUp() {
        jdbc.update("delete from idempotency_record");
        EXECUTIONS.set(0);
        FAIL_NEXT_EXECUTION.set(false);
        processingEntered = new CountDownLatch(0);
        processingMayContinue = new CountDownLatch(0);
        jdbc.update("update shedlock set lock_until = now() - interval '1 minute' where name = 'IdempotencyRecordCleanupJob'");
    }

    private BillingOperationOutcome<TestResult> run(String key, String amount) {
        return pipeline.runOperation(operation, new TestCommand(key, new BigDecimal(amount)));
    }

    private double replays() {
        return meterRegistry.counter("billing.idempotency.replays").count();
    }

    @Test
    @Tag("D-136")
    void theSameKeyWithTheSameRequestRunsOnceAndReturnsTheStoredResponseWithItsOriginalStatus() {
        double replaysBefore = replays();

        BillingOperationOutcome<TestResult> first = run("key-1", "80000.00");
        BillingOperationOutcome<TestResult> second = run("key-1", "80000.00");

        assertThat(EXECUTIONS.get()).isEqualTo(1);
        assertThat(first).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(second).isEqualTo(first);
        assertThat(((BillingOperationOutcome.Processed<TestResult>) second).result().reference()).isEqualTo("payment-1");
        assertThat(jdbc.queryForObject("select response_status from idempotency_record where idempotency_key = 'key-1'", Integer.class)).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isEqualTo(1);
        assertThat(replays()).isEqualTo(replaysBefore + 1);
        assertThat(jdbc.queryForObject("select response_body->>'amount' from idempotency_record where idempotency_key = 'key-1'", String.class)).isEqualTo("80000.00");
    }

    private final AtomicInteger openedAuthorizationRequests = new AtomicInteger();
    private final AtomicBoolean authorizationRequired = new AtomicBoolean();

    private BillingOperationPipeline pipelineThatMayRequireAuthorization() {
        com.red_escuela.backend.billing.operation.PreProcessingHandler<TestCommand> authorization = (type, context, prior) ->
                authorizationRequired.get() && prior == PriorAuthorization.ABSENT
                        ? PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "needs the director")
                        : PreProcessingResult.proceed();
        com.red_escuela.backend.billing.operation.OperationHandlerRegistry registry = new com.red_escuela.backend.billing.operation.OperationHandlerRegistry(List.of(
                new OperationPreHandlers(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS,
                        List.of(new IdempotencyKeyHandler<TestCommand>(recordService, objectMapper, TestResult.class), authorization))));
        return new BillingOperationPipeline(registry, (type, context, role, reason, expiresOn) -> (long) (100 + openedAuthorizationRequests.incrementAndGet()), eventPublisher);
    }

    private BillingOperationOutcome<TestResult> runInTransaction(BillingOperationPipeline manualPipeline, boolean resume, TestCommand command) {
        return transactionTemplate.execute(status -> resume ? manualPipeline.resumeAuthorizedOperation(operation, command) : manualPipeline.runOperation(operation, command));
    }

    @Test
    @Tag("D-136")
    void aRequestThatWaitedForAuthorizationIsReplayedAsStillWaitingAndNeverOpensASecondRequest() {
        authorizationRequired.set(true);
        BillingOperationPipeline manual = pipelineThatMayRequireAuthorization();
        TestCommand command = new TestCommand("key-auth", new BigDecimal("10.00"));

        BillingOperationOutcome<TestResult> first = runInTransaction(manual, false, command);
        BillingOperationOutcome<TestResult> second = runInTransaction(manual, false, command);

        assertThat(first).isEqualTo(new BillingOperationOutcome.AwaitingAuthorization<TestResult>(101L));
        assertThat(second).isEqualTo(first);
        assertThat(openedAuthorizationRequests.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select response_status from idempotency_record where idempotency_key = 'key-auth'", Integer.class)).isEqualTo(202);
        assertThat(EXECUTIONS.get()).isZero();
    }

    @Test
    @Tag("D-136")
    void onceTheAuthorizationIsApprovedTheSameKeyProcessesOnceAndFromThenOnIsReplayedAsProcessed() {
        authorizationRequired.set(true);
        BillingOperationPipeline manual = pipelineThatMayRequireAuthorization();
        TestCommand command = new TestCommand("key-resume", new BigDecimal("10.00"));
        runInTransaction(manual, false, command);

        BillingOperationOutcome<TestResult> resumed = runInTransaction(manual, true, command);
        BillingOperationOutcome<TestResult> replayed = runInTransaction(manual, false, command);

        assertThat(resumed).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(replayed).isEqualTo(resumed);
        assertThat(EXECUTIONS.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select response_status from idempotency_record where idempotency_key = 'key-resume'", Integer.class)).isEqualTo(201);
    }

    @Test
    @Tag("D-136")
    void theSameAmountWrittenWithDifferentScaleIsTheSameRequest() {
        run("key-scale", "100");
        BillingOperationOutcome<TestResult> second = run("key-scale", "100.00");

        assertThat(second).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(EXECUTIONS.get()).isEqualTo(1);
    }

    @Test
    @Tag("D-136")
    void theSameKeyFromAnotherActorIsAConflictWithoutEffects() {
        pipeline.runOperation(operation, new TestCommand("key-actor", new BigDecimal("10.00"), StatusChangeActor.ofPerson(1L)));

        assertThatThrownBy(() -> pipeline.runOperation(operation, new TestCommand("key-actor", new BigDecimal("10.00"), StatusChangeActor.ofPerson(2L))))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(EXECUTIONS.get()).isEqualTo(1);
    }

    @Test
    @Tag("D-136")
    void twoConcurrentResumptionsAfterAuthorizationProcessTheOperationOnce() throws Exception {
        authorizationRequired.set(true);
        BillingOperationPipeline manual = pipelineThatMayRequireAuthorization();
        TestCommand command = new TestCommand("key-concurrent-resume", new BigDecimal("10.00"));
        runInTransaction(manual, false, command);
        processingEntered = new CountDownLatch(1);
        processingMayContinue = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<BillingOperationOutcome<TestResult>> firstResumption = executor.submit(() -> runInTransaction(manual, true, command));
            assertThat(processingEntered.await(30, TimeUnit.SECONDS)).isTrue();
            Future<BillingOperationOutcome<TestResult>> secondResumption = executor.submit(() -> runInTransaction(manual, true, command));
            awaitUntilABackendWaitsForALock();
            processingMayContinue.countDown();

            BillingOperationOutcome<TestResult> first = firstResumption.get(30, TimeUnit.SECONDS);
            BillingOperationOutcome<TestResult> second = secondResumption.get(30, TimeUnit.SECONDS);

            assertThat(EXECUTIONS.get()).isEqualTo(1);
            assertThat(second).isEqualTo(first).isInstanceOf(BillingOperationOutcome.Processed.class);
        } finally {
            processingMayContinue.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-136")
    void twoThreadsWithTheSameKeyProcessOnceAndBothGetTheSameResponseWithoutAnyServerError() throws Exception {
        processingEntered = new CountDownLatch(1);
        processingMayContinue = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BillingOperationOutcome<TestResult>>> futures = List.of(
                    executor.submit(() -> {
                        start.await();
                        return run("key-race", "80000.00");
                    }),
                    executor.submit(() -> {
                        start.await();
                        return run("key-race", "80000.00");
                    }));
            start.countDown();
            assertThat(processingEntered.await(30, TimeUnit.SECONDS)).isTrue();
            awaitUntilABackendWaitsForALock();
            processingMayContinue.countDown();

            BillingOperationOutcome<TestResult> first = futures.get(0).get(30, TimeUnit.SECONDS);
            BillingOperationOutcome<TestResult> second = futures.get(1).get(30, TimeUnit.SECONDS);

            assertThat(EXECUTIONS.get()).isEqualTo(1);
            assertThat(first).isEqualTo(second).isInstanceOf(BillingOperationOutcome.Processed.class);
            assertThat(jdbc.queryForObject("select count(*) from idempotency_record where idempotency_key = 'key-race'", Long.class)).isEqualTo(1);
        } finally {
            processingMayContinue.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-136")
    void ifTheFirstThreadFailsTheSecondOneWaitingOnTheSameKeyRunsTheOperationItself() throws Exception {
        processingEntered = new CountDownLatch(1);
        processingMayContinue = new CountDownLatch(1);
        FAIL_NEXT_EXECUTION.set(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> failing = executor.submit(() -> {
                try {
                    run("key-failing-race", "5.00");
                    return "processed";
                } catch (IllegalStateException failure) {
                    return "failed";
                }
            });
            assertThat(processingEntered.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Object> waiting = executor.submit(() -> run("key-failing-race", "5.00"));
            awaitUntilABackendWaitsForALock();
            processingMayContinue.countDown();

            assertThat(failing.get(30, TimeUnit.SECONDS)).isEqualTo("failed");
            assertThat(waiting.get(30, TimeUnit.SECONDS)).isInstanceOf(BillingOperationOutcome.Processed.class);
            assertThat(EXECUTIONS.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from idempotency_record where idempotency_key = 'key-failing-race'", Long.class)).isEqualTo(1);
        } finally {
            processingMayContinue.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitUntilABackendWaitsForALock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock'", Long.class) == 0) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    @Test
    @Tag("D-136")
    void theSameKeyWithAnotherAmountOrAnotherOperationIsAConflictWithoutEffects() {
        run("key-conflict", "100.00");
        String storedHash = jdbc.queryForObject("select request_hash from idempotency_record where idempotency_key = 'key-conflict'", String.class);

        assertThatThrownBy(() -> run("key-conflict", "100.01")).isInstanceOf(IdempotencyKeyConflictException.class)
                .satisfies(exception -> assertThat(((IdempotencyKeyConflictException) exception).violations()).extracting(violation -> violation.code()).containsExactly("IDEMPOTENCY_KEY_CONFLICT"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                recordService.claimKeyOrFindStoredResponse("key-conflict", BillingOperationType.ISSUE_INVOICE, storedHash, PriorAuthorization.ABSENT)))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(EXECUTIONS.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select request_hash from idempotency_record where idempotency_key = 'key-conflict'", String.class)).isEqualTo(storedHash);
        assertThat(jdbc.queryForObject("select operation_type from idempotency_record where idempotency_key = 'key-conflict'", String.class)).isEqualTo("CREATE_LATE_PAYMENT_SETTINGS");
    }

    @Test
    @Tag("D-136")
    void anOperationThatFailsAfterClaimingItsKeyLeavesNoRecordAndTheRetryProcessesNormally() {
        FAIL_NEXT_EXECUTION.set(true);

        assertThatThrownBy(() -> run("key-retry", "7.00")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isZero();
        BillingOperationOutcome<TestResult> retried = run("key-retry", "7.00");

        assertThat(retried).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(EXECUTIONS.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isEqualTo(1);
    }

    @Test
    @Tag("D-136")
    void theCleanupDeletesOnlyTheRecordsOlderThanSevenDays() {
        LocalDateTime now = LocalDateTime.of(2027, 3, 20, 3, 30);
        MutableClock.INSTANCE.setDateTime(now);
        jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('old', 'REGISTER_PAYMENT', 'h', ?)", now.minusDays(8));
        jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('recent', 'REGISTER_PAYMENT', 'h', ?)", now.minusDays(6));
        jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('edge', 'REGISTER_PAYMENT', 'h', ?)", now.minusDays(7).plusMinutes(1));

        cleanupJob.deleteExpiredRecords();

        assertThat(jdbc.queryForList("select idempotency_key from idempotency_record order by idempotency_key", String.class)).containsExactly("edge", "recent");
    }

    @Test
    @Tag("D-136")
    void theCleanupRunsInASingleInstanceBecauseTheJobLockIsHeldWhileAnotherInstanceRuns() {
        LocalDateTime now = LocalDateTime.of(2027, 3, 20, 3, 30);
        MutableClock.INSTANCE.setDateTime(now);
        jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('old', 'REGISTER_PAYMENT', 'h', ?)", now.minusDays(8));
        Optional<SimpleLock> heldByOtherInstance = lockProvider.lock(new LockConfiguration(Instant.now(), "IdempotencyRecordCleanupJob", Duration.ofMinutes(5), Duration.ZERO));
        assertThat(heldByOtherInstance).isPresent();
        try {
            cleanupJob.deleteExpiredRecords();
            assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isEqualTo(1);
        } finally {
            heldByOtherInstance.get().unlock();
        }

        cleanupJob.deleteExpiredRecords();

        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isZero();
    }

    @Test
    @Tag("D-136")
    void claimingOutsideATransactionIsRefusedSoAKeyCanNeverBeClaimedWithoutItsOperation() {
        assertThatThrownBy(() -> recordService.claimKeyOrFindStoredResponse("key-outside", BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS, "h", PriorAuthorization.ABSENT))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
