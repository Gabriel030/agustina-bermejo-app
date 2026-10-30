package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.policy.NightlyBillingCompletedTodayContext;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemAttemptRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.billing.service.BillingConfigurationAlertService;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyInvoiceRunSweepIsolationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final LocalDate FIRST = LocalDate.of(2026, 4, 1);
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-01T07:00:00Z"), ZoneOffset.UTC);

    private MonthlyInvoiceRunRepository runRepository;
    private MonthlyInvoiceCandidateFinder candidateFinder;
    private MonthlyInvoiceRunItemProcessor itemProcessor;
    private MonthlyInvoiceRunExecutor executor;

    @BeforeEach
    void setUp() {
        runRepository = mock(MonthlyInvoiceRunRepository.class);
        candidateFinder = mock(MonthlyInvoiceCandidateFinder.class);
        itemProcessor = mock(MonthlyInvoiceRunItemProcessor.class);
        NightlyBillingRunRepository nightlyRepository = mock(NightlyBillingRunRepository.class);
        when(runRepository.saveAndFlush(any(MonthlyInvoiceRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.save(any(MonthlyInvoiceRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.findByPeriodAndStatusAndStartedAtBefore(any(), any(), any())).thenReturn(List.of());
        when(nightlyRepository.findByBusinessDate(FIRST)).thenReturn(Optional.empty());
        executor = new MonthlyInvoiceRunExecutor(runRepository, nightlyRepository, candidateFinder, itemProcessor, mock(BillingConfigurationAlertService.class),
                PolicySet.<NightlyBillingCompletedTodayContext>of(List.of()), new SimpleMeterRegistry(), clock);
        when(candidateFinder.findProgramEnrollmentIdsAfter(any(), anyLong(), anyBoolean())).thenReturn(List.of());
        when(candidateFinder.findCourseEnrollmentIdsAfter(any(), anyLong())).thenReturn(List.of());
    }

    private static FailedInvoiceRetryScheduleResolver defaultScheduleResolver() {
        FailedInvoiceRetryScheduleResolver resolver = mock(FailedInvoiceRetryScheduleResolver.class);
        when(resolver.resolveScheduleEffectiveOn(any())).thenReturn(new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24)));
        return resolver;
    }

    private static MonthlyInvoiceRunItem itemWith(MonthlyInvoiceEnrollmentType type, Long enrollmentId, MonthlyInvoiceRunItemResult result) {
        MonthlyInvoiceItemOutcome outcome = result == MonthlyInvoiceRunItemResult.FAILED
                ? MonthlyInvoiceItemOutcome.failed("Boom", "boom")
                : MonthlyInvoiceItemOutcome.issued(List.of(1L));
        return MonthlyInvoiceRunItem.recordItemOutcome(1L, type, enrollmentId, outcome, LocalDateTime.of(2026, 4, 1, 7, 0), new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24)));
    }

    @Test
    @Tag("D-448")
    void anEnrollmentFoundByTwoSweepsIsProcessedOnlyOncePerRun() {
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(0L), eq(false))).thenReturn(List.of(7L));
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(7L), eq(false))).thenReturn(List.of());
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(0L), eq(true))).thenReturn(List.of(7L, 8L));
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(8L), eq(true))).thenReturn(List.of());
        when(itemProcessor.issueAndRecordItem(any(), eq(MonthlyInvoiceEnrollmentType.PROGRAM), anyLong(), any(), any()))
                .thenAnswer(invocation -> itemWith(MonthlyInvoiceEnrollmentType.PROGRAM, invocation.getArgument(2), MonthlyInvoiceRunItemResult.ISSUED));

        MonthlyInvoiceRun run = executor.runMonthlyInvoicesOfPeriod(APRIL, FIRST, MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();

        verify(itemProcessor, times(1)).issueAndRecordItem(any(), eq(MonthlyInvoiceEnrollmentType.PROGRAM), eq(7L), any(), any());
        verify(itemProcessor, times(1)).issueAndRecordItem(any(), eq(MonthlyInvoiceEnrollmentType.PROGRAM), eq(8L), any(), any());
        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }

    @Test
    @Tag("D-448")
    void aProgramAndACourseEnrollmentWithTheSameIdAreBothProcessed() {
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(0L), eq(false))).thenReturn(List.of(5L));
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(5L), anyBoolean())).thenReturn(List.of());
        when(candidateFinder.findCourseEnrollmentIdsAfter(eq(APRIL), eq(0L))).thenReturn(List.of(5L));
        when(candidateFinder.findCourseEnrollmentIdsAfter(eq(APRIL), eq(5L))).thenReturn(List.of());
        when(itemProcessor.issueAndRecordItem(any(), any(), anyLong(), any(), any()))
                .thenAnswer(invocation -> itemWith(invocation.getArgument(1), invocation.getArgument(2), MonthlyInvoiceRunItemResult.ISSUED));

        executor.runMonthlyInvoicesOfPeriod(APRIL, FIRST, MonthlyInvoiceRunTrigger.SCHEDULED);

        verify(itemProcessor).issueAndRecordItem(any(), eq(MonthlyInvoiceEnrollmentType.PROGRAM), eq(5L), any(), any());
        verify(itemProcessor).issueAndRecordItem(any(), eq(MonthlyInvoiceEnrollmentType.COURSE), eq(5L), any(), any());
    }

    @Test
    @Tag("D-448")
    void aFailedEnrollmentDoesNotStopTheFollowingOnesAndTheRunFinishesWithFailures() {
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(0L), eq(false))).thenReturn(List.of(1L, 2L, 3L));
        when(candidateFinder.findProgramEnrollmentIdsAfter(eq(APRIL), eq(3L), anyBoolean())).thenReturn(List.of());
        when(itemProcessor.issueAndRecordItem(any(), any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(2);
            return itemWith(MonthlyInvoiceEnrollmentType.PROGRAM, id, id == 2L ? MonthlyInvoiceRunItemResult.FAILED : MonthlyInvoiceRunItemResult.ISSUED);
        });

        MonthlyInvoiceRun run = executor.runMonthlyInvoicesOfPeriod(APRIL, FIRST, MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();

        verify(itemProcessor).issueAndRecordItem(any(), any(), eq(3L), any(), any());
        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED_WITH_FAILURES);
    }

    @Test
    @Tag("D-448")
    void anItemThatCannotBeSavedIsReturnedAsFailedWithItsCodeAndRetriedAsFailed() {
        MonthlyInvoiceRunItemRepository itemRepository = mock(MonthlyInvoiceRunItemRepository.class);
        MonthlyInstallmentRowProcessor rowProcessor = mock(MonthlyInstallmentRowProcessor.class);
        when(rowProcessor.reportProgramEnrollmentIssuance(anyLong(), any(), any()))
                .thenReturn(new MonthlyInstallmentRowProcessor.MonthlyIssuanceReport(List.of(9L), List.of(), List.of()));
        when(itemRepository.save(any(MonthlyInvoiceRunItem.class)))
                .thenThrow(new IllegalStateException("duplicate item"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MonthlyInvoiceRunItemProcessor processor = new MonthlyInvoiceRunItemProcessor(rowProcessor, itemRepository, mock(MonthlyInvoiceRunItemAttemptRepository.class), defaultScheduleResolver(),
                mock(BillingRunAlertPublisher.class), clock);

        MonthlyInvoiceRunItem item = processor.issueAndRecordItem(1L, MonthlyInvoiceEnrollmentType.PROGRAM, 4L, APRIL, FIRST);

        ArgumentCaptor<MonthlyInvoiceRunItem> saved = ArgumentCaptor.forClass(MonthlyInvoiceRunItem.class);
        verify(itemRepository, times(2)).save(saved.capture());
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.FAILED);
        assertThat(item.getErrorCode()).isEqualTo("IllegalStateException");
        assertThat(item.getMessage()).isEqualTo("duplicate item");
        assertThat(saved.getAllValues().get(1)).isSameAs(item);
    }

    @Test
    @Tag("D-448")
    void anItemThatCannotBeSavedEvenAsFailedStillCountsAsFailedWithoutThrowing() {
        MonthlyInvoiceRunItemRepository itemRepository = mock(MonthlyInvoiceRunItemRepository.class);
        MonthlyInstallmentRowProcessor rowProcessor = mock(MonthlyInstallmentRowProcessor.class);
        when(rowProcessor.reportCourseEnrollmentIssuance(anyLong(), any(), any()))
                .thenReturn(new MonthlyInstallmentRowProcessor.MonthlyIssuanceReport(List.of(), List.of(), List.of()));
        doThrow(new IllegalStateException("database down")).when(itemRepository).save(any(MonthlyInvoiceRunItem.class));
        MonthlyInvoiceRunItemProcessor processor = new MonthlyInvoiceRunItemProcessor(rowProcessor, itemRepository, mock(MonthlyInvoiceRunItemAttemptRepository.class), defaultScheduleResolver(),
                mock(BillingRunAlertPublisher.class), clock);

        MonthlyInvoiceRunItem item = processor.issueAndRecordItem(1L, MonthlyInvoiceEnrollmentType.COURSE, 4L, APRIL, FIRST);

        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.FAILED);
        assertThat(item.getEnrollmentId()).isEqualTo(4L);
        assertThat(item.getAttempts()).isEqualTo(1);
        verify(itemRepository, times(2)).save(any(MonthlyInvoiceRunItem.class));
    }

    @Test
    @Tag("D-160")
    void aSweepFailureOutsideTheItemsAbortsTheRunWithTheSweepAbortedCode() {
        when(candidateFinder.findProgramEnrollmentIdsAfter(any(), anyLong(), anyBoolean())).thenThrow(new IllegalStateException("query failed"));

        MonthlyInvoiceRun run = executor.runMonthlyInvoicesOfPeriod(APRIL, FIRST, MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();

        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.FAILED);
        assertThat(run.getFailureCode()).isEqualTo("SWEEP_ABORTED");
        verify(itemProcessor, never()).issueAndRecordItem(any(), any(), anyLong(), any(), any());
    }
}
