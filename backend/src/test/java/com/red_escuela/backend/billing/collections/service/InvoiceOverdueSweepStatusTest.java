package com.red_escuela.backend.billing.collections.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final InvoiceOverdueSweepService sweep = new InvoiceOverdueSweepService(step, logRepository, new ObjectMapper(), Clock.system(ZoneOffset.UTC));

    private BatchExecutionLog runWith(OverdueDetectionResult result) {
        when(step.detectOverdueInvoices(TODAY)).thenReturn(result);
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
}
