package com.red_escuela.backend.billing.run.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-451")
class BillingRunStepOutcomeTest {

    @Test
    void onlySkippedAndFailedSubjectsBecomeItemsAndTheRestIsACounter() {
        BillingRunStepOutcome outcome = BillingRunStepOutcome.ofCandidates(1200, List.of(
                BillingRunItemRecord.skippedSubject(BillingRunSubjectType.INVOICE, 1L, "NO_LATE_PAYMENT_TERMS", "sin versión"),
                BillingRunItemRecord.skippedSubject(BillingRunSubjectType.INVOICE, 2L, "NO_LATE_PAYMENT_TERMS", "sin versión"),
                BillingRunItemRecord.skippedSubject(BillingRunSubjectType.INVOICE, 3L, "NO_LATE_PAYMENT_TERMS", "sin versión"),
                BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 4L, "SUBJECT_PROCESSING_FAILED", "boom")));

        assertThat(outcome.items()).hasSize(4);
        assertThat(outcome.skipped()).isEqualTo(3);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(1196);
        assertThat(outcome.hasErrors()).isTrue();
    }

    @Test
    void theSameSubjectReportedTwiceIsRecordedOnce() {
        BillingRunStepOutcome outcome = BillingRunStepOutcome.ofCandidates(5, List.of(
                BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 7L, "A", "first"),
                BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 7L, "B", "second")));

        assertThat(outcome.items()).singleElement().extracting(BillingRunItemRecord::errorCode).isEqualTo("A");
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(4);
    }

    @Test
    void skippedSubjectsAloneAreNotErrors() {
        BillingRunStepOutcome outcome = BillingRunStepOutcome.ofCandidates(2, List.of(
                BillingRunItemRecord.skippedSubject(BillingRunSubjectType.INVOICE, 1L, "NO_LATE_PAYMENT_TERMS", "sin versión")));

        assertThat(outcome.hasErrors()).isFalse();
        assertThat(outcome.processed()).isEqualTo(1);
    }

    @Test
    void theProcessedCounterNeverGoesNegative() {
        BillingRunStepOutcome outcome = BillingRunStepOutcome.ofCandidates(0, List.of(BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 1L, "A", "x")));

        assertThat(outcome.processed()).isZero();
    }
}
