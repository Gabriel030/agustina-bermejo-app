package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemAttemptRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FailedMonthlyInvoiceRetrierTest {
    private static final FailedInvoiceRetrySchedule SCHEDULE = new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24));
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);

    private final MutableClock clock = MutableClock.INSTANCE;
    private MonthlyInvoiceRunItemRepository itemRepository;
    private MonthlyInvoiceRunItemAttemptRepository attemptRepository;
    private MonthlyInvoiceRunRepository runRepository;
    private MonthlyInvoiceRunItemProcessor itemProcessor;
    private BillingRunAlertPublisher alertPublisher;
    private FailedMonthlyInvoiceItemRetryProcessor retryProcessor;
    private FailedMonthlyInvoiceRetrier retrier;

    @BeforeEach
    void setUp() {
        itemRepository = mock(MonthlyInvoiceRunItemRepository.class);
        attemptRepository = mock(MonthlyInvoiceRunItemAttemptRepository.class);
        runRepository = mock(MonthlyInvoiceRunRepository.class);
        itemProcessor = mock(MonthlyInvoiceRunItemProcessor.class);
        alertPublisher = mock(BillingRunAlertPublisher.class);
        FailedInvoiceRetryScheduleResolver resolver = mock(FailedInvoiceRetryScheduleResolver.class);
        when(resolver.resolveScheduleEffectiveOn(any())).thenReturn(SCHEDULE);
        retryProcessor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, runRepository, itemProcessor, resolver, alertPublisher, clock);
        retrier = new FailedMonthlyInvoiceRetrier(itemRepository, retryProcessor, new SimpleMeterRegistry(), clock);
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startRealDirectRun(FIRST_DAY, FIRST_DAY, MonthlyInvoiceRunTrigger.SCHEDULED, FIRST_DAY.atTime(4, 0));
        when(runRepository.findById(anyLong())).thenReturn(Optional.of(run));
        when(itemRepository.save(any(MonthlyInvoiceRunItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private MonthlyInvoiceRunItem failedItemDueAt(LocalDateTime firstFailureAt, FailedInvoiceRetrySchedule schedule) {
        MonthlyInvoiceRunItem item = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.PROGRAM, 7L, MonthlyInvoiceItemOutcome.failed("E", "boom"),
                firstFailureAt, schedule);
        ReflectionTestUtils.setField(item, "id", 1L);
        when(itemRepository.findByIdForUpdate(any())).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    @Tag("D-160")
    void outsideTheSixToTenPmWindowTheRetrierDoesNotEvenLookForItems() {
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 5, 59));
        assertThat(retrier.retryDueFailedItems()).isZero();
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 22, 0));
        assertThat(retrier.retryDueFailedItems()).isZero();

        verifyNoInteractions(itemRepository);
        verifyNoInteractions(itemProcessor);
    }

    @Test
    @Tag("D-160")
    void insideTheWindowEveryDueItemIsRetriedAndOneFailureDoesNotStopTheOthers() {
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 6, 0));
        when(itemRepository.findDueFailedItemIds(any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L));
        FailedMonthlyInvoiceItemRetryProcessor throwingOnSecond = mock(FailedMonthlyInvoiceItemRetryProcessor.class);
        MonthlyInvoiceRunItem done = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.PROGRAM, 7L, MonthlyInvoiceItemOutcome.issued(List.of(1L)),
                LocalDateTime.of(2026, 10, 1, 6, 0), SCHEDULE);
        when(throwingOnSecond.retryItemIfDue(1L)).thenReturn(Optional.of(done));
        when(throwingOnSecond.retryItemIfDue(2L)).thenThrow(new IllegalStateException("boom"));
        when(throwingOnSecond.retryItemIfDue(3L)).thenReturn(Optional.of(done));

        int retried = new FailedMonthlyInvoiceRetrier(itemRepository, throwingOnSecond, new SimpleMeterRegistry(), clock).retryDueFailedItems();

        assertThat(retried).isEqualTo(2);
        verify(throwingOnSecond).retryItemIfDue(3L);
    }

    @Test
    @Tag("D-054")
    void aRetryOnTheFourthDayIssuesWithThePricingDateOfTheFirstDayOfThePeriod() {
        MonthlyInvoiceRunItem item = failedItemDueAt(LocalDateTime.of(2026, 10, 1, 4, 0), SCHEDULE);
        clock.setDateTime(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.issued(List.of(5L)));

        retryProcessor.retryItemIfDue(1L);

        verify(itemProcessor).attemptEnrollmentMonthIssuance(MonthlyInvoiceEnrollmentType.PROGRAM, 7L, YearMonth.of(2026, 10), FIRST_DAY);
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
    }

    @Test
    @Tag("D-160")
    void anItemNotYetDueIsNotRetriedNorRecorded() {
        failedItemDueAt(LocalDateTime.of(2026, 10, 1, 4, 0), SCHEDULE);
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 4, 59));

        assertThat(retryProcessor.retryItemIfDue(1L)).isEmpty();

        verifyNoInteractions(itemProcessor);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @Tag("D-160")
    void theLastFailedRetryExhaustsTheItemRecordsItsAttemptAndRaisesOneAlert() {
        MonthlyInvoiceRunItem item = failedItemDueAt(LocalDateTime.of(2026, 10, 1, 4, 0), new FailedInvoiceRetrySchedule(1, List.of(1)));
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 6, 0));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.failed("E2", "again"));
        when(runRepository.findById(eq(1L))).thenReturn(Optional.of(MonthlyInvoiceRun.startRealDirectRun(FIRST_DAY, FIRST_DAY, MonthlyInvoiceRunTrigger.SCHEDULED,
                FIRST_DAY.atTime(4, 0))));
        FailedInvoiceRetryScheduleResolver oneRetry = mock(FailedInvoiceRetryScheduleResolver.class);
        when(oneRetry.resolveScheduleEffectiveOn(any())).thenReturn(new FailedInvoiceRetrySchedule(1, List.of(1)));
        FailedMonthlyInvoiceItemRetryProcessor processor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, runRepository, itemProcessor,
                oneRetry, alertPublisher, clock);

        processor.retryItemIfDue(1L);

        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED);
        verify(alertPublisher).publishItemRetriesExhaustedAlert(item);
        verify(attemptRepository).save(any());
    }

    @Test
    @Tag("D-160")
    void aRetryThatFailsButHasMoreRetriesLeftRaisesNoAlert() {
        failedItemDueAt(LocalDateTime.of(2026, 10, 1, 4, 0), SCHEDULE);
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 6, 0));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.failed("E2", "again"));
        doThrow(new AssertionError("no alert expected")).when(alertPublisher).publishItemRetriesExhaustedAlert(any());

        MonthlyInvoiceRunItem retried = retryProcessor.retryItemIfDue(1L).orElseThrow();

        assertThat(retried.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.FAILED);
        assertThat(retried.getAttempts()).isEqualTo(2);
        assertThat(retried.getNextRetryAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
    }
}
