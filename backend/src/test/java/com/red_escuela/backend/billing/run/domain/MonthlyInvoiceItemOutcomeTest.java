package com.red_escuela.backend.billing.run.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-160")
class MonthlyInvoiceItemOutcomeTest {
    private static final FailedInvoiceRetrySchedule SCHEDULE = new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 4, 0);

    @Test
    void notApplicableTakesTheFirstDecisionThatIsNotInformative() {
        MonthlyInvoiceItemOutcome outcome = MonthlyInvoiceItemOutcome.notApplicable(List.of("ELIGIBLE", "CYCLE", "INSTALLMENT_CAP_REACHED", "SUSPENDED_OR_WITHDRAWN"));

        assertThat(outcome.result()).isEqualTo(MonthlyInvoiceRunItemResult.NOT_APPLICABLE);
        assertThat(outcome.decisionCode()).isEqualTo("INSTALLMENT_CAP_REACHED");
    }

    @Test
    void notApplicableWithoutAnyDenialFallsBackToNoChargesPlanned() {
        assertThat(MonthlyInvoiceItemOutcome.notApplicable(List.of("ELIGIBLE", "CYCLE")).decisionCode()).isEqualTo(MonthlyInvoiceItemOutcome.NO_CHARGES_PLANNED);
        assertThat(MonthlyInvoiceItemOutcome.notApplicable(List.of()).decisionCode()).isEqualTo(MonthlyInvoiceItemOutcome.NO_CHARGES_PLANNED);
    }

    @Test
    void aFailureKeepsOnlyTheFirstFiveHundredCharactersOfItsMessage() {
        MonthlyInvoiceItemOutcome outcome = MonthlyInvoiceItemOutcome.failed("IllegalStateException", "x".repeat(900));

        assertThat(outcome.message()).hasSize(500);
        assertThat(MonthlyInvoiceItemOutcome.failed("E", null).message()).isNull();
    }

    @Test
    void aFailedItemIsScheduledForRetryOneHourLaterWithItsFirstAttempt() {
        MonthlyInvoiceRunItem item = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.PROGRAM, 7L, MonthlyInvoiceItemOutcome.failed("E", "boom"), NOW, SCHEDULE);

        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getNextRetryAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void anIssuedItemHasNoAttemptsNorRetry() {
        MonthlyInvoiceRunItem item = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.COURSE, 7L, MonthlyInvoiceItemOutcome.issued(List.of(10L)), NOW, SCHEDULE);

        assertThat(item.getAttempts()).isZero();
        assertThat(item.getNextRetryAt()).isNull();
        assertThat(item.getInvoiceIds()).containsExactly(10L);
    }
}
