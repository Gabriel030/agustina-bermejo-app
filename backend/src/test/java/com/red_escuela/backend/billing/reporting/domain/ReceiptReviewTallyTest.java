package com.red_escuela.backend.billing.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptReviewTallyTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    void oneReceiptReviewedAfterFourBusinessDaysIsOutsideTheThreeDayTarget() {
        ReceiptReviewTally tally = new ReceiptReviewTally(3);

        tally.recordUpload(ReceiptOutcome.APPROVED, MONDAY, MONDAY.plusDays(1));
        tally.recordUpload(ReceiptOutcome.APPLIED_TO_DEBT, MONDAY, MONDAY.plusDays(3));
        tally.recordUpload(ReceiptOutcome.REJECTED, MONDAY, MONDAY.plusDays(4));
        tally.recordUpload(ReceiptOutcome.PENDING_REVIEW, MONDAY, null);

        assertThat(tally.uploaded()).isEqualTo(4);
        assertThat(tally.approved()).isEqualTo(1);
        assertThat(tally.appliedToDebt()).isEqualTo(1);
        assertThat(tally.rejected()).isEqualTo(1);
        assertThat(tally.reviewedAfterTarget()).isEqualTo(1);
        assertThat(tally.averageReviewBusinessDays().orElseThrow().days()).isEqualByComparingTo("2.67");
    }

    @Test
    void reviewingExactlyOnTheTargetIsNotLate() {
        ReceiptReviewTally tally = new ReceiptReviewTally(3);

        tally.recordUpload(ReceiptOutcome.APPROVED, MONDAY, MONDAY.plusDays(3));

        assertThat(tally.reviewedAfterTarget()).isZero();
    }

    @Test
    void withoutReviewedReceiptsThereIsNoAverage() {
        ReceiptReviewTally tally = new ReceiptReviewTally(3);

        tally.recordUpload(ReceiptOutcome.PENDING_REVIEW, MONDAY, null);

        assertThat(tally.averageReviewBusinessDays()).isEmpty();
    }

    @Test
    void theTargetMustBeAtLeastOneBusinessDay() {
        assertThatThrownBy(() -> new ReceiptReviewTally(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
