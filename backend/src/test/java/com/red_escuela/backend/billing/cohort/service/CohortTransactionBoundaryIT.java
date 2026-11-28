package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.billing.configuration.service.DirectConfigurationPublisher;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramCohortUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class CohortTransactionBoundaryIT extends AbstractStudentDiscountIssuingIT {
    @org.springframework.beans.factory.annotation.Autowired private com.red_escuela.backend.testsupport.PreceptorTestFactory preceptorTestFactory;

    private static final String[] OBSERVED_TABLES = {"program_cohort", "configuration_version", "authorization_request"};

    @Autowired
    private CreateProgramCohortUseCase createCohort;

    @Autowired
    private UpdateCohortEntryFeeDiscountUseCase updateEntryDiscount;

    @Autowired
    private DirectConfigurationPublisher publisher;

    @Autowired
    private BillingOperationPipeline operationPipeline;

    @Test
    void cohortCreationFailingAfterPublishingTheInitialDiscountLeavesNoCohortNorVersion() {
        Long template = factory.newTemplate(offering, 2027);
        authenticateAs(director);
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbc, OBSERVED_TABLES);

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                Object published = invocation.callRealMethod();
                steps.run(() -> { });
                return published;
            }).when(publisher).publishVersion(any(), any(), any(), any(), any());
            createCohort.create(template, secondIntakeRequest());
        });
    }

    @Test
    void cohortCreationFailingWhileRunningTheProposalLeavesNoCohortNorRequest() {
        Long template = factory.newTemplate(offering, 2027);
        authenticateAs(administrative);
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbc, OBSERVED_TABLES);

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                Object outcome = invocation.callRealMethod();
                steps.run(() -> { });
                return outcome;
            }).when(operationPipeline).runOperation(any(), any());
            createCohort.create(template, new CreateProgramCohortRequest(CohortIntake.SECOND, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1),
                    LocalDate.of(2027, 8, 1), List.of(preceptorTestFactory.createPreceptorId()), Percentage.ofPoints(new BigDecimal("20"))));
        });
    }

    @Test
    void cohortCreationWithoutFailuresPersistsTheCohortAndTheInitialDiscountVersion() {
        Long template = factory.newTemplate(offering, 2027);
        authenticateAs(director);
        long cohortsBefore = count("program_cohort");
        long versionsBefore = count("configuration_version");

        createCohort.create(template, secondIntakeRequest());

        assertThat(count("program_cohort")).isEqualTo(cohortsBefore + 1);
        assertThat(count("configuration_version")).isEqualTo(versionsBefore + 1);
    }

    @Test
    void entryDiscountUpdateFailingAfterTheOperationLeavesNoNewVersion() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("10")));
        authenticateAs(director);
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbc, OBSERVED_TABLES);

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                Object outcome = invocation.callRealMethod();
                steps.run(() -> { });
                return outcome;
            }).when(operationPipeline).runOperation(any(), any());
            updateEntryDiscount.update(cohortId, Percentage.ofPoints(new BigDecimal("25")));
        });
    }

    @Test
    void entryDiscountUpdateByDirectorWithoutFailuresPublishesTheNewVersion() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("10")));
        authenticateAs(director);
        long versionsBefore = count("configuration_version");

        updateEntryDiscount.update(cohortId, Percentage.ofPoints(new BigDecimal("25")));

        assertThat(count("configuration_version")).isEqualTo(versionsBefore + 1);
    }

    @Test
    void entryDiscountUpdateAwaitingAuthorizationStillKeepsTheAuthorizationRequest() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("10")));
        authenticateAs(administrative);
        long requestsBefore = count("authorization_request");

        assertThatThrownBy(() -> updateEntryDiscount.update(cohortId, Percentage.ofPoints(new BigDecimal("25"))))
                .isInstanceOf(AuthorizationPendingException.class);

        assertThat(count("authorization_request")).isEqualTo(requestsBefore + 1);
        String pendingVersions = "select id from configuration_version where authorization_request_id in (select id from authorization_request where requested_by_id = ?)";
        jdbc.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
        try {
            jdbc.update("delete from configuration_version_status_change where configuration_version_id in (" + pendingVersions + ")", administrative.getId());
            jdbc.update("delete from configuration_version where id in (" + pendingVersions + ")", administrative.getId());
        } finally {
            jdbc.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        }
    }

    private CreateProgramCohortRequest secondIntakeRequest() {
        return new CreateProgramCohortRequest(CohortIntake.SECOND, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 8, 1),
                List.of(preceptorTestFactory.createPreceptorId()));
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
