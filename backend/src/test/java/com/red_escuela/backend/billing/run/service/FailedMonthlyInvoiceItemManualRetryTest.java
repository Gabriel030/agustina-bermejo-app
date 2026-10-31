package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemAttempt;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunItemNotFoundException;
import com.red_escuela.backend.billing.run.exception.RunItemNotRetryableException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemAttemptRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FailedMonthlyInvoiceItemManualRetryTest {
    private static final FailedInvoiceRetrySchedule SCHEDULE = new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24));
    private static final FailedInvoiceRetrySchedule NO_RETRIES = new FailedInvoiceRetrySchedule(0, List.of());
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);
    private static final long RUN_ID = 1L;
    private static final long ITEM_ID = 5L;

    private final MutableClock clock = MutableClock.INSTANCE;
    private MonthlyInvoiceRunItemRepository itemRepository;
    private MonthlyInvoiceRunItemAttemptRepository attemptRepository;
    private MonthlyInvoiceRunItemProcessor itemProcessor;
    private BillingRunAlertPublisher alertPublisher;
    private FailedMonthlyInvoiceItemRetryProcessor retryProcessor;

    @BeforeEach
    void setUp() {
        itemRepository = mock(MonthlyInvoiceRunItemRepository.class);
        attemptRepository = mock(MonthlyInvoiceRunItemAttemptRepository.class);
        MonthlyInvoiceRunRepository runRepository = mock(MonthlyInvoiceRunRepository.class);
        itemProcessor = mock(MonthlyInvoiceRunItemProcessor.class);
        alertPublisher = mock(BillingRunAlertPublisher.class);
        FailedInvoiceRetryScheduleResolver resolver = mock(FailedInvoiceRetryScheduleResolver.class);
        when(resolver.resolveScheduleEffectiveOn(any())).thenReturn(SCHEDULE);
        retryProcessor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, runRepository, itemProcessor, resolver, alertPublisher, clock, new MonthlyInvoiceIssuanceStrategies());
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startRealDirectRun(FIRST_DAY, FIRST_DAY, MonthlyInvoiceRunTrigger.SCHEDULED, FIRST_DAY.atTime(4, 0));
        when(runRepository.findById(anyLong())).thenReturn(Optional.of(run));
        when(itemRepository.save(any(MonthlyInvoiceRunItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private MonthlyInvoiceRunItem lockedItem(MonthlyInvoiceItemOutcome firstOutcome, FailedInvoiceRetrySchedule schedule) {
        MonthlyInvoiceRunItem item = MonthlyInvoiceRunItem.recordItemOutcome(RUN_ID, MonthlyInvoiceEnrollmentType.PROGRAM, 7L, firstOutcome, FIRST_DAY.atTime(4, 0), schedule);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        when(itemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    @Tag("D-515")
    void anItemRetriedInAReviewRunIsRetriedAsADraftOfThatRun() {
        MonthlyInvoiceRunRepository reviewRunRepository = mock(MonthlyInvoiceRunRepository.class);
        MonthlyInvoiceRun reviewRun = mock(MonthlyInvoiceRun.class);
        when(reviewRun.getId()).thenReturn(99L);
        when(reviewRun.getPeriod()).thenReturn(FIRST_DAY);
        when(reviewRun.getIssuanceMode()).thenReturn(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode.REVIEW);
        when(reviewRunRepository.findById(anyLong())).thenReturn(Optional.of(reviewRun));
        FailedInvoiceRetryScheduleResolver resolver = mock(FailedInvoiceRetryScheduleResolver.class);
        when(resolver.resolveScheduleEffectiveOn(any())).thenReturn(SCHEDULE);
        FailedMonthlyInvoiceItemRetryProcessor reviewProcessor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, reviewRunRepository, itemProcessor,
                resolver, alertPublisher, clock, new MonthlyInvoiceIssuanceStrategies());
        lockedItem(MonthlyInvoiceItemOutcome.failed("E", "price missing"), NO_RETRIES);
        clock.setDateTime(LocalDateTime.of(2026, 10, 4, 23, 30));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.issued(List.of(41L)));

        reviewProcessor.retryItemManually(RUN_ID, ITEM_ID);

        verify(itemProcessor).attemptEnrollmentMonthIssuance(MonthlyInvoiceEnrollmentType.PROGRAM, 7L, YearMonth.of(2026, 10), FIRST_DAY,
                new InvoiceIssuanceDestination.DraftOfMonthlyRun(99L));
    }

    @Test
    @Tag("D-160")
    @Tag("D-054")
    void anExhaustedItemRetriedOnTheFourthDayIsIssuedWithTheFirstDayPricingAndRecordsTheNextAttempt() {
        MonthlyInvoiceRunItem item = lockedItem(MonthlyInvoiceItemOutcome.failed("E", "price missing"), NO_RETRIES);
        clock.setDateTime(LocalDateTime.of(2026, 10, 4, 23, 30));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.issued(List.of(41L)));

        MonthlyInvoiceRunItem retried = retryProcessor.retryItemManually(RUN_ID, ITEM_ID);

        verify(itemProcessor).attemptEnrollmentMonthIssuance(MonthlyInvoiceEnrollmentType.PROGRAM, 7L, YearMonth.of(2026, 10), FIRST_DAY, InvoiceIssuanceDestination.EMIT_NOW);
        assertThat(retried).isSameAs(item);
        assertThat(retried.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(retried.getAttempts()).isEqualTo(2);
        assertThat(retried.getInvoiceIds()).containsExactly(41L);
        ArgumentCaptor<MonthlyInvoiceRunItemAttempt> attempt = ArgumentCaptor.forClass(MonthlyInvoiceRunItemAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(attempt.getValue().getAttemptedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 23, 30));
        assertThat(attempt.getValue().getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        verify(alertPublisher, never()).publishItemRetriesExhaustedAlert(any());
    }

    @Test
    @Tag("D-160")
    void aFailedItemThatIsNotDueYetCanBeRetriedByStaffRightAway() {
        MonthlyInvoiceRunItem item = lockedItem(MonthlyInvoiceItemOutcome.failed("E", "boom"), SCHEDULE);
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 4, 5));
        assertThat(item.isDueForRetryAt(LocalDateTime.of(2026, 10, 1, 4, 5))).isFalse();
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.issued(List.of(41L)));

        MonthlyInvoiceRunItem retried = retryProcessor.retryItemManually(RUN_ID, ITEM_ID);

        assertThat(retried.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(retried.getNextRetryAt()).isNull();
    }

    @Test
    @Tag("D-160")
    void anItemInAnyOtherResultIsRejectedWithoutTouchingAnythingElse() {
        for (MonthlyInvoiceItemOutcome closed : List.of(MonthlyInvoiceItemOutcome.issued(List.of(1L)), MonthlyInvoiceItemOutcome.alreadyIssued(List.of(1L)),
                MonthlyInvoiceItemOutcome.notApplicable(List.of("SUSPENDED_OR_WITHDRAWN")))) {
            lockedItem(closed, SCHEDULE);

            assertThatThrownBy(() -> retryProcessor.retryItemManually(RUN_ID, ITEM_ID)).isInstanceOf(RunItemNotRetryableException.class)
                    .hasMessageContaining(closed.result().name());
        }

        verifyNoInteractions(itemProcessor);
        verifyNoInteractions(attemptRepository);
        verifyNoInteractions(alertPublisher);
    }

    @Test
    @Tag("D-160")
    void anItemOfAnotherRunOrAMissingOneIsNotFound() {
        lockedItem(MonthlyInvoiceItemOutcome.failed("E", "boom"), SCHEDULE);

        assertThatThrownBy(() -> retryProcessor.retryItemManually(RUN_ID + 1, ITEM_ID)).isInstanceOf(MonthlyInvoiceRunItemNotFoundException.class);
        assertThatThrownBy(() -> retryProcessor.retryItemManually(RUN_ID, ITEM_ID + 1)).isInstanceOf(MonthlyInvoiceRunItemNotFoundException.class);

        verifyNoInteractions(itemProcessor);
    }

    @Test
    @Tag("D-160")
    void aManualRetryThatFailsAgainOnAnExhaustedItemKeepsItExhaustedCountsTheAttemptAndDoesNotRaiseAnotherAlert() {
        MonthlyInvoiceRunItem item = lockedItem(MonthlyInvoiceItemOutcome.failed("E", "boom"), NO_RETRIES);
        clock.setDateTime(LocalDateTime.of(2026, 10, 2, 9, 0));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.failed("E2", "still"));
        FailedInvoiceRetryScheduleResolver exhaustedForever = mock(FailedInvoiceRetryScheduleResolver.class);
        when(exhaustedForever.resolveScheduleEffectiveOn(any())).thenReturn(NO_RETRIES);
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startRealDirectRun(FIRST_DAY, FIRST_DAY, MonthlyInvoiceRunTrigger.SCHEDULED, FIRST_DAY.atTime(4, 0));
        MonthlyInvoiceRunRepository runRepository = mock(MonthlyInvoiceRunRepository.class);
        when(runRepository.findById(anyLong())).thenReturn(Optional.of(run));
        FailedMonthlyInvoiceItemRetryProcessor processor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, runRepository, itemProcessor,
                exhaustedForever, alertPublisher, clock, new MonthlyInvoiceIssuanceStrategies());

        processor.retryItemManually(RUN_ID, ITEM_ID);

        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED);
        assertThat(item.getAttempts()).isEqualTo(2);
        assertThat(item.getNextRetryAt()).isNull();
        assertThat(item.getErrorCode()).isEqualTo("E2");
        verify(attemptRepository).save(any());
        verify(alertPublisher, never()).publishItemRetriesExhaustedAlert(any());
    }

    @Test
    @Tag("D-160")
    void aManualRetryThatFailsOnTheLastAllowedTurnOfAFailedItemRaisesTheExhaustedAlertOnce() {
        FailedInvoiceRetrySchedule oneRetry = new FailedInvoiceRetrySchedule(1, List.of(1));
        MonthlyInvoiceRunItem item = lockedItem(MonthlyInvoiceItemOutcome.failed("E", "boom"), oneRetry);
        clock.setDateTime(LocalDateTime.of(2026, 10, 1, 4, 10));
        when(itemProcessor.attemptEnrollmentMonthIssuance(any(), anyLong(), any(), any(), any())).thenReturn(MonthlyInvoiceItemOutcome.failed("E2", "again"));
        FailedInvoiceRetryScheduleResolver resolver = mock(FailedInvoiceRetryScheduleResolver.class);
        when(resolver.resolveScheduleEffectiveOn(any())).thenReturn(oneRetry);
        MonthlyInvoiceRunRepository runRepository = mock(MonthlyInvoiceRunRepository.class);
        when(runRepository.findById(anyLong())).thenReturn(Optional.of(MonthlyInvoiceRun.startRealDirectRun(FIRST_DAY, FIRST_DAY, MonthlyInvoiceRunTrigger.SCHEDULED,
                FIRST_DAY.atTime(4, 0))));
        FailedMonthlyInvoiceItemRetryProcessor processor = new FailedMonthlyInvoiceItemRetryProcessor(itemRepository, attemptRepository, runRepository, itemProcessor,
                resolver, alertPublisher, clock, new MonthlyInvoiceIssuanceStrategies());

        processor.retryItemManually(RUN_ID, ITEM_ID);

        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED);
        verify(alertPublisher).publishItemRetriesExhaustedAlert(item);
    }
}
