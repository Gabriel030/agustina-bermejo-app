package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AtomicityTestSupportIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext applicationContext;

    private AtomicityTestSupport support;

    @BeforeEach
    void createFixtureTables() {
        jdbcTemplate.execute("create table atomicity_fixture_a (id int primary key)");
        jdbcTemplate.execute("create table atomicity_fixture_b (id int primary key)");
        jdbcTemplate.execute("create table atomicity_fixture_c (id int primary key)");
        support = AtomicityTestSupport.observing(jdbcTemplate, "atomicity_fixture_a", "atomicity_fixture_b", "atomicity_fixture_c");
    }

    @AfterEach
    void dropFixtureTables() {
        jdbcTemplate.execute("drop table atomicity_fixture_a, atomicity_fixture_b, atomicity_fixture_c");
    }

    @Test
    void transactionalFlowFailingAtSecondOfThreeStepsLeavesNothingPersisted() {
        TransactionTemplate composer = new TransactionTemplate(transactionManager);

        support.assertNothingPersistedWhenFailingAtStep(2, steps -> composer.executeWithoutResult(status -> runThreeSteps(steps)));

        assertThat(rowCount("atomicity_fixture_a")).isZero();
    }

    @Test
    void flowWithoutTransactionFailingAtSecondStepIsReportedAsPartialWrite() {
        assertThatThrownBy(() -> support.assertNothingPersistedWhenFailingAtStep(2, this::runThreeSteps))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("partial writes")
                .hasMessageContaining("atomicity_fixture_a=1");
    }

    @Test
    void flowThatDoesNotReachTheFailingStepIsReported() {
        assertThatThrownBy(() -> support.assertNothingPersistedWhenFailingAtStep(4, steps -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> runThreeSteps(steps))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("finished without failing at step 4");
    }

    @Test
    void failureSwallowedInsideTheFlowIsReportedBecauseTheFlowNeverFailed() {
        assertThatThrownBy(() -> support.assertNothingPersistedWhenFailingAtStep(2, steps -> {
            try {
                runThreeSteps(steps);
            } catch (AtomicityTestSupport.InjectedFailure swallowed) {
                return;
            }
        }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("finished without failing at step 2");
    }

    @Test
    void runningInsideATestTransactionIsRejected() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThatThrownBy(() -> support.assertNothingPersistedWhenFailingAtStep(2, this::runThreeSteps))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("must not run inside a test transaction"));
    }

    @Test
    void openInViewInterceptorIsRegisteredBecauseNoPropertyDisablesIt() {
        assertThat(applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
    }

    private void runThreeSteps(AtomicityTestSupport.FailingStep steps) {
        steps.run(() -> jdbcTemplate.update("insert into atomicity_fixture_a values (1)"));
        steps.run(() -> jdbcTemplate.update("insert into atomicity_fixture_b values (1)"));
        steps.run(() -> jdbcTemplate.update("insert into atomicity_fixture_c values (1)"));
    }

    private long rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }
}
