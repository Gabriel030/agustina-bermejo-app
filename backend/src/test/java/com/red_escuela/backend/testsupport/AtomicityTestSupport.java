package com.red_escuela.backend.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AtomicityTestSupport {
    private final JdbcTemplate jdbcTemplate;
    private final String[] observedTables;

    private AtomicityTestSupport(JdbcTemplate jdbcTemplate, String[] observedTables) {
        this.jdbcTemplate = jdbcTemplate;
        this.observedTables = observedTables;
    }

    public static AtomicityTestSupport observing(JdbcTemplate jdbcTemplate, String... observedTables) {
        return new AtomicityTestSupport(jdbcTemplate, observedTables);
    }

    public void assertNothingPersistedWhenFailingAtStep(int failingStep, java.util.function.Consumer<FailingStep> flow) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssertionError("an atomicity test must not run inside a test transaction: it would hide a missing transaction in the flow");
        }
        Map<String, Long> before = countRows();
        FailingStep steps = new FailingStep(failingStep);
        try {
            flow.accept(steps);
        } catch (InjectedFailure expected) {
            assertFailureWasReached(steps);
            assertSameCounts(before, countRows());
            return;
        }
        throw new AssertionError("the flow finished without failing at step " + failingStep + " (" + steps.executedSteps() + " steps ran)");
    }

    private void assertFailureWasReached(FailingStep steps) {
        if (!steps.failureWasInjected()) {
            throw new AssertionError("an injected failure was caught but the failing step was never reached");
        }
    }

    private void assertSameCounts(Map<String, Long> before, Map<String, Long> after) {
        if (!before.equals(after)) {
            throw new AssertionError("the flow left partial writes. before=" + before + " after=" + after);
        }
    }

    private Map<String, Long> countRows() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : observedTables) {
            counts.put(table, jdbcTemplate.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    public static final class FailingStep {
        private final int failingStep;
        private int executedSteps;
        private boolean failureInjected;

        private FailingStep(int failingStep) {
            if (failingStep < 1) {
                throw new IllegalArgumentException("failingStep starts at 1");
            }
            this.failingStep = failingStep;
        }

        public void run(Runnable step) {
            executedSteps++;
            if (executedSteps == failingStep) {
                failureInjected = true;
                throw new InjectedFailure(failingStep);
            }
            step.run();
        }

        int executedSteps() {
            return executedSteps;
        }

        boolean failureWasInjected() {
            return failureInjected;
        }
    }

    public static final class InjectedFailure extends RuntimeException {
        InjectedFailure(int step) {
            super("injected failure at step " + step);
        }
    }
}
