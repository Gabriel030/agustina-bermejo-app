package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyInstallmentIssuanceJobFailureIsolationTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 3);

    @Test
    void aFailingEnrollmentDoesNotStopTheOthersAndIsLoggedWithItsId() {
        PaymentPlanSelectionRepository selections = mock(PaymentPlanSelectionRepository.class);
        BatchExecutionLogRepository logs = mock(BatchExecutionLogRepository.class);
        MonthlyInstallmentRowProcessor processor = mock(MonthlyInstallmentRowProcessor.class);
        when(selections.findProgramEnrollmentIdsWithActivePlan(any(), anyList(), eq(MONTH), eq(0L), any())).thenReturn(List.of(1L, 2L, 3L));
        when(selections.findProgramEnrollmentIdsWithActivePlan(any(), anyList(), eq(MONTH), eq(3L), any())).thenReturn(List.of());
        when(selections.findCourseEnrollmentIdsWithActivePlan(any(), any(), eq(MONTH), eq(0L), any())).thenReturn(List.of());
        when(processor.issueForProgramEnrollment(1L, MONTH)).thenReturn(InstallmentIssuanceOutcome.ISSUED);
        when(processor.issueForProgramEnrollment(2L, MONTH)).thenThrow(new IllegalStateException("boom"));
        when(processor.issueForProgramEnrollment(3L, MONTH)).thenReturn(InstallmentIssuanceOutcome.ISSUED);
        MonthlyInstallmentIssuanceJob job = new MonthlyInstallmentIssuanceJob(
                selections, logs, processor, new ObjectMapper(), new SimpleMeterRegistry(),
                java.time.Clock.systemDefaultZone());

        job.run(MONTH);

        verify(processor).issueForProgramEnrollment(3L, MONTH);
        verify(processor, never()).issueForCourseEnrollment(any(), any());
        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(logs).save(captor.capture());
        BatchExecutionLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE);
        assertThat(saved.getErrors()).contains("\"enrollmentId\":2");
    }
}
