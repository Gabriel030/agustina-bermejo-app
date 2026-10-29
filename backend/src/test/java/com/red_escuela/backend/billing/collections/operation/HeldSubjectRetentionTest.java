package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.LateFeeAccrualService;
import com.red_escuela.backend.billing.collections.service.LateFeeCorrectionOutcome;
import com.red_escuela.backend.billing.collections.service.LateFeeWithdrawalCorrectionService;
import com.red_escuela.backend.billing.collections.service.LatePaymentTimeline;
import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.collections.service.OverdueTransitionOutcome;
import com.red_escuela.backend.billing.invoice.service.PendingInvoiceQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-356")
@Tag("D-459")
class HeldSubjectRetentionTest {
    private static final LocalDate NIGHT = LocalDate.of(2031, 10, 7);

    private final PendingInvoiceQueryService pendingInvoices = mock(PendingInvoiceQueryService.class);
    private final OverdueInvoiceTransitionService transitions = mock(OverdueInvoiceTransitionService.class);
    private final LateFeeAccrualService accrual = mock(LateFeeAccrualService.class);
    private final LateFeeWithdrawalCorrectionService correction = mock(LateFeeWithdrawalCorrectionService.class);

    private static HeldSubjectFinder holding(Long... ids) {
        Set<Long> held = Set.of(ids);
        return candidates -> candidates.stream().filter(held::contains).collect(java.util.stream.Collectors.toSet());
    }

    private static Map<String, Object> heldEntry(String key, long id) {
        return Map.of(key, id, "decisionCode", "INCONSISTENT_ACCOUNT", "message", HeldSubjectFinder.INCONSISTENT_ACCOUNT_MESSAGE);
    }

    @Test
    void overdueDetectionSkipsTheHeldInvoiceAndStillMarksTheOthers() {
        when(pendingInvoices.findPendingInvoiceIdsDueBefore(NIGHT, 0L, 200)).thenReturn(List.of(10L, 20L, 30L));
        when(pendingInvoices.findPendingInvoiceIdsDueBefore(NIGHT, 30L, 200)).thenReturn(List.of());
        when(transitions.markAsOverdueWhenPastDueDate(any(), eq(NIGHT))).thenReturn(OverdueTransitionOutcome.MARKED_AS_OVERDUE);

        OverdueDetectionResult result = new OverdueInvoiceDetectionStep(pendingInvoices, transitions).detectOverdueInvoices(NIGHT, holding(20L));

        assertThat(result.candidates()).isEqualTo(3);
        assertThat(result.marked()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).singleElement().isEqualTo(heldEntry("invoiceId", 20L));
        verify(transitions, never()).markAsOverdueWhenPastDueDate(eq(20L), any());
        verify(transitions).markAsOverdueWhenPastDueDate(10L, NIGHT);
        verify(transitions).markAsOverdueWhenPastDueDate(30L, NIGHT);
    }

    @Test
    void overdueDetectionWithoutHeldSubjectsTouchesEveryInvoice() {
        when(pendingInvoices.findPendingInvoiceIdsDueBefore(NIGHT, 0L, 200)).thenReturn(List.of(10L, 20L));
        when(pendingInvoices.findPendingInvoiceIdsDueBefore(NIGHT, 20L, 200)).thenReturn(List.of());
        when(transitions.markAsOverdueWhenPastDueDate(any(), eq(NIGHT))).thenReturn(OverdueTransitionOutcome.MARKED_AS_OVERDUE);

        OverdueDetectionResult result = new OverdueInvoiceDetectionStep(pendingInvoices, transitions).detectOverdueInvoices(NIGHT);

        assertThat(result.marked()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void lateFeeAccrualSkipsTheHeldInvoiceInBothSweepsAndStillReconcilesTheOthers() {
        LatePaymentTimeline timeline = mock(LatePaymentTimeline.class);
        when(accrual.timelineOf(NIGHT)).thenReturn(timeline);
        when(pendingInvoices.findOverdueLateFeeOriginIdsNotCoveredUpToAfter(eq(0L), any(), anyInt())).thenReturn(List.of(10L, 20L));
        when(pendingInvoices.findOverdueLateFeeOriginIdsNotCoveredUpToAfter(eq(20L), any(), anyInt())).thenReturn(List.of());
        when(accrual.findRecentlySettledOriginIdsAfter(eq(0L), any(), anyInt())).thenReturn(List.of(20L, 40L));
        when(accrual.findRecentlySettledOriginIdsAfter(eq(40L), any(), anyInt())).thenReturn(List.of());
        when(accrual.reconcileLateFeesOfInvoice(any(), eq(NIGHT), eq(timeline))).thenReturn(1);
        when(accrual.reconcileLateFeesOfSettledInvoice(any(), eq(NIGHT), eq(timeline))).thenReturn(1);

        LateFeeAccrualResult result = new LateFeeAccrualStep(pendingInvoices, accrual).accrueLateFees(NIGHT, holding(20L));

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.settlementCandidates()).isEqualTo(2);
        assertThat(result.debitNotesIssued()).isEqualTo(2);
        assertThat(result.settlementDebitNotesRecovered()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).containsExactly(heldEntry("invoiceId", 20L), heldEntry("invoiceId", 20L));
        verify(accrual, never()).reconcileLateFeesOfInvoice(eq(20L), any(), any());
        verify(accrual, never()).reconcileLateFeesOfSettledInvoice(eq(20L), any(), any());
        verify(accrual).reconcileLateFeesOfInvoice(10L, NIGHT, timeline);
        verify(accrual).reconcileLateFeesOfSettledInvoice(40L, NIGHT, timeline);
    }

    @Test
    void lateFeeWithdrawalCorrectionSkipsTheHeldChargeAndStillCorrectsTheOthers() {
        when(correction.findChargeIdsWithUncorrectedDaysAfter(0L, 200)).thenReturn(List.of(10L, 20L, 30L));
        when(correction.findChargeIdsWithUncorrectedDaysAfter(30L, 200)).thenReturn(List.of());
        when(correction.correctLateFeeCharge(10L, NIGHT)).thenReturn(LateFeeCorrectionOutcome.CORRECTED);
        when(correction.correctLateFeeCharge(30L, NIGHT)).thenReturn(LateFeeCorrectionOutcome.DEFERRED);

        LateFeeCorrectionSweepResult result = new LateFeeWithdrawalCorrectionStep(correction).correctLateFeesChargedAfterTheEnrollmentEnd(NIGHT, holding(20L));

        assertThat(result.candidates()).isEqualTo(3);
        assertThat(result.corrected()).isEqualTo(1);
        assertThat(result.deferred()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).singleElement().isEqualTo(heldEntry("lateFeeChargeId", 20L));
        verify(correction, never()).correctLateFeeCharge(eq(20L), any());
    }

    @Test
    void theNoneFinderHoldsNothing() {
        assertThat(HeldSubjectFinder.NONE.findHeldAmong(List.of(1L, 2L))).isEmpty();
    }
}
