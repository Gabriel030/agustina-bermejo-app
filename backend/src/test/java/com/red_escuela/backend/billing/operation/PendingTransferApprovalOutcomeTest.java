package com.red_escuela.backend.billing.operation;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingTransferApprovalOutcomeTest {

    private static boolean outside(List<Long> receiptIds, List<Long> settledIds) {
        return new PendingTransferApprovalOutcome(1L, receiptIds, settledIds, List.of()).settledAnInvoiceOutsideTheReceipt();
    }

    @Test
    void noSettledInvoicesIsNotOutsideTheReceipt() {
        assertThat(outside(List.of(1L, 2L), List.of())).isFalse();
        assertThat(outside(List.of(), List.of())).isFalse();
    }

    @Test
    void settledInvoicesWithEmptyReceiptAreOutside() {
        assertThat(outside(List.of(), List.of(5L))).isTrue();
    }

    @Test
    void settledInvoicesAllInsideTheReceiptAreNotOutside() {
        assertThat(outside(List.of(1L, 2L, 3L), List.of(3L, 1L))).isFalse();
        assertThat(outside(List.of(1L, 2L), List.of(1L, 2L))).isFalse();
    }

    @Test
    void oneSettledInvoiceOutsideTheReceiptIsDetected() {
        assertThat(outside(List.of(1L, 2L), List.of(1L, 9L))).isTrue();
        assertThat(outside(List.of(1L, 2L), List.of(9L))).isTrue();
    }

    @Test
    void partialOverlapIsOutside() {
        assertThat(outside(List.of(1L, 2L, 3L), List.of(2L, 3L, 4L))).isTrue();
    }

    @Test
    void duplicatedIdsDoNotChangeTheResult() {
        assertThat(outside(List.of(1L, 1L, 2L), List.of(1L, 1L, 2L, 2L))).isFalse();
        assertThat(outside(List.of(1L, 1L), List.of(1L, 1L, 7L, 7L))).isTrue();
    }

    @Test
    void receiptLargerThanSettledIsNotOutside() {
        assertThat(outside(List.of(1L, 2L, 3L, 4L), List.of(2L))).isFalse();
    }
}
