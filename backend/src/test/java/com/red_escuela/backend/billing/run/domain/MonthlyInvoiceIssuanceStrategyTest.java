package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceIssuanceStrategies;
import com.red_escuela.backend.billing.run.service.EffectiveMonthlyInvoiceIssuanceModeResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInvoiceIssuanceStrategyTest {

    private final MonthlyInvoiceIssuanceStrategies strategies = new MonthlyInvoiceIssuanceStrategies();

    @Test
    @Tag("D-052")
    void everyIssuanceModeHasItsOwnStrategy() {
        for (MonthlyInvoiceIssuanceMode mode : MonthlyInvoiceIssuanceMode.values()) {
            assertThat(strategies.strategyOf(mode).mode()).isEqualTo(mode);
        }
    }

    @Test
    @Tag("D-052")
    void directModeEmitsNowAndReviewModeLeavesDraftsOfTheRun() {
        assertThat(strategies.strategyOf(MonthlyInvoiceIssuanceMode.DIRECT).destinationOfRun(7L)).isEqualTo(InvoiceIssuanceDestination.EMIT_NOW);
        assertThat(strategies.strategyOf(MonthlyInvoiceIssuanceMode.REVIEW).destinationOfRun(7L)).isEqualTo(new InvoiceIssuanceDestination.DraftOfMonthlyRun(7L));
    }

    @Test
    @Tag("D-514")
    void reviewRunWithDraftsWaitsForReviewEvenWithFailures() {
        MonthlyInvoiceIssuanceStrategy review = strategies.strategyOf(MonthlyInvoiceIssuanceMode.REVIEW);

        assertThat(review.finishEventOf(1, 3)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW);
        assertThat(review.finishEventOf(0, 3)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW);
    }

    @Test
    @Tag("D-514")
    void reviewRunWithoutDraftsFinishesLikeADirectRun() {
        MonthlyInvoiceIssuanceStrategy review = strategies.strategyOf(MonthlyInvoiceIssuanceMode.REVIEW);

        assertThat(review.finishEventOf(0, 0)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN);
        assertThat(review.finishEventOf(2, 0)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES);
    }

    @Test
    @Tag("D-514")
    void directRunNeverWaitsForReview() {
        MonthlyInvoiceIssuanceStrategy direct = strategies.strategyOf(MonthlyInvoiceIssuanceMode.DIRECT);

        assertThat(direct.finishEventOf(0, 5)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN);
        assertThat(direct.finishEventOf(1, 5)).isEqualTo(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES);
    }

    @Test
    @Tag("D-512")
    void newRunsAreForcedToDirectUntilTheBatchApprovalExists() {
        assertThat(new EffectiveMonthlyInvoiceIssuanceModeResolver().resolveModeOfNewRun()).isEqualTo(MonthlyInvoiceIssuanceMode.DIRECT);
    }
}
