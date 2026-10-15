package com.red_escuela.backend.billing.collections.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.collections.operation.LateFeeCorrectionSweepResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeWithdrawalCorrectionStep;
import com.red_escuela.backend.billing.collections.operation.OverdueDetectionResult;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceOverdueSweepStatusTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 18);

    private final OverdueInvoiceDetectionStep step = mock(OverdueInvoiceDetectionStep.class);
    private final BatchExecutionLogRepository logRepository = mock(BatchExecutionLogRepository.class);
    private final LateFeeAccrualStep accrualStep = mock(LateFeeAccrualStep.class);
    private final LateFeeWithdrawalCorrectionStep correctionStep = mock(LateFeeWithdrawalCorrectionStep.class);
    private final InvoiceOverdueSweepService sweep = new InvoiceOverdueSweepService(step, accrualStep, correctionStep, logRepository, new ObjectMapper(), Clock.system(ZoneOffset.UTC));

    private BatchExecutionLog runWith(OverdueDetectionResult result) {
        return runWith(result, LateFeeCorrectionSweepResult.nothing());
    }

    private BatchExecutionLog runWith(OverdueDetectionResult result, LateFeeCorrectionSweepResult correction) {
        when(step.detectOverdueInvoices(TODAY)).thenReturn(result);
        when(accrualStep.accrueLateFees(TODAY)).thenReturn(LateFeeAccrualResult.nothing());
        when(correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(TODAY)).thenReturn(correction);
        sweep.run(TODAY);
        ArgumentCaptor<BatchExecutionLog> saved = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(logRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @Tag("D-288")
    void aRunWithoutSkippedNorFailedInvoicesIsASuccess() {
        assertThat(runWith(new OverdueDetectionResult(3, 3, 0, 0, List.of())).getStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
    }

    @Test
    @Tag("D-288")
    void invoicesSkippedForLackOfLatePaymentTermsLeaveThePartialStatusAndTheirIds() {
        BatchExecutionLog log = runWith(new OverdueDetectionResult(3, 2, 1, 0, List.of(Map.of("invoiceId", 9L, "message", "Sin versión de mora aplicable"))));

        assertThat(log.getStatus()).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE);
        assertThat(log.getFailedCount()).isEqualTo(1);
        assertThat(log.getErrors()).contains("9");
    }

    @Test
    @Tag("D-288")
    void aRunWhereNothingWasMarkedAndSomethingFailedIsAFailure() {
        assertThat(runWith(new OverdueDetectionResult(1, 0, 0, 1, List.of(Map.of("invoiceId", 1L)))).getStatus()).isEqualTo(BatchExecutionStatus.FAILED);
    }

    @Test
    @Tag("D-061")
    @Tag("D-288")
    void theSweepDetectsFirstThenAccruesAndWritesASingleLogWithBothResults() {
        when(step.detectOverdueInvoices(TODAY)).thenReturn(new OverdueDetectionResult(2, 2, 0, 0, List.of()));
        when(accrualStep.accrueLateFees(TODAY)).thenReturn(new LateFeeAccrualResult(3, 4, 1, List.of(Map.of("invoiceId", 5L))));
        when(correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(TODAY)).thenReturn(LateFeeCorrectionSweepResult.nothing());

        sweep.run(TODAY);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(step, accrualStep, logRepository);
        order.verify(step).detectOverdueInvoices(TODAY);
        order.verify(accrualStep).accrueLateFees(TODAY);
        ArgumentCaptor<BatchExecutionLog> saved = ArgumentCaptor.forClass(BatchExecutionLog.class);
        order.verify(logRepository).save(saved.capture());
        assertThat(saved.getValue().getCandidateCount()).isEqualTo(5);
        assertThat(saved.getValue().getProcessedCount()).isEqualTo(6);
        assertThat(saved.getValue().getFailedCount()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE);
    }

    @Test
    @Tag("D-375")
    void correctionsIssuedAreCountedAsProcessedAndASweepWithOnlyThemIsASuccess() {
        BatchExecutionLog log = runWith(new OverdueDetectionResult(0, 0, 0, 0, List.of()), new LateFeeCorrectionSweepResult(3, 3, 0, 0, List.of()));

        assertThat(log.getStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
        assertThat(log.getCandidateCount()).isEqualTo(3);
        assertThat(log.getProcessedCount()).isEqualTo(3);
        assertThat(log.getFailedCount()).isZero();
    }

    @Test
    @Tag("D-375")
    void aFailedCorrectionLeavesThePartialStatusItsErrorAndTheCounts() {
        BatchExecutionLog log = runWith(new OverdueDetectionResult(0, 0, 0, 0, List.of()),
                new LateFeeCorrectionSweepResult(4, 2, 1, 1, List.of(Map.of("lateFeeChargeId", 77L, "message", "Boom"))));

        assertThat(log.getStatus()).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE);
        assertThat(log.getCandidateCount()).isEqualTo(4);
        assertThat(log.getProcessedCount()).isEqualTo(2);
        assertThat(log.getFailedCount()).isEqualTo(1);
        assertThat(log.getErrors()).contains("77");
    }

    @Test
    @Tag("D-375")
    void aSweepWhereOnlyCorrectionsFailedAndNothingElseWasProcessedIsAFailure() {
        BatchExecutionLog log = runWith(new OverdueDetectionResult(0, 0, 0, 0, List.of()),
                new LateFeeCorrectionSweepResult(1, 0, 0, 1, List.of(Map.of("lateFeeChargeId", 5L, "message", "Boom"))));

        assertThat(log.getStatus()).isEqualTo(BatchExecutionStatus.FAILED);
        assertThat(log.getErrors()).contains("5");
    }
}
