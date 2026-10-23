package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.events.PaymentReceiptReviewOverdueEvent;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository.ReceiptAwaitingReviewAlert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-268")
class OverdueReceiptReviewAlerterTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate MONDAY = LocalDate.of(2027, 3, 15);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);

    private final BankTransferReceiptRecordRepository receipts = mock(BankTransferReceiptRecordRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final BusinessDayCalendar weekdaysOnly = day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;

    private OverdueReceiptReviewAlerter alerterOn(LocalDate today, int threshold) {
        Clock clock = Clock.fixed(today.atTime(9, 0).atZone(BUENOS_AIRES).toInstant(), BUENOS_AIRES);
        return new OverdueReceiptReviewAlerter(receipts, weekdaysOnly, publisher, clock, threshold);
    }

    private ReceiptAwaitingReviewAlert candidate(long receiptId, LocalDate uploadedOn) {
        ReceiptAwaitingReviewAlert candidate = mock(ReceiptAwaitingReviewAlert.class);
        when(candidate.getReceiptId()).thenReturn(receiptId);
        when(candidate.getPaymentId()).thenReturn(receiptId + 1000);
        when(candidate.getInvoiceId()).thenReturn(receiptId + 2000);
        when(candidate.getUploadedAt()).thenReturn(uploadedOn.atTime(10, 0));
        return candidate;
    }

    @Test
    void aReceiptAlreadyAlertedByAnotherInstanceDoesNotPublishAgain() {
        ReceiptAwaitingReviewAlert pending = candidate(1, MONDAY);
        when(receipts.findPendingWithoutReviewAlertUploadedBefore(any())).thenReturn(List.of(pending));
        when(receipts.markReviewAlertSent(eq(1L), any())).thenReturn(0);

        int alerted = alerterOn(THURSDAY, 3).alertOverdueReviews();

        assertThat(alerted).isZero();
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aCandidateThatHasNotReachedTheThresholdIsNeitherMarkedNorPublished() {
        ReceiptAwaitingReviewAlert pending = candidate(1, MONDAY.plusDays(1));
        when(receipts.findPendingWithoutReviewAlertUploadedBefore(any())).thenReturn(List.of(pending));

        int alerted = alerterOn(THURSDAY, 3).alertOverdueReviews();

        assertThat(alerted).isZero();
        verify(receipts, never()).markReviewAlertSent(any(), any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theEventCarriesTheBusinessDaysCountedAndTheUploadDate() {
        ReceiptAwaitingReviewAlert pending = candidate(7, MONDAY);
        when(receipts.findPendingWithoutReviewAlertUploadedBefore(any())).thenReturn(List.of(pending));
        when(receipts.markReviewAlertSent(eq(7L), any())).thenReturn(1);

        int alerted = alerterOn(MONDAY.plusDays(7), 3).alertOverdueReviews();

        assertThat(alerted).isEqualTo(1);
        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event -> event instanceof PaymentReceiptReviewOverdueEvent overdue
                && overdue.receiptId() == 7L && overdue.paymentId() == 1007L && overdue.invoiceId() == 2007L && overdue.uploadedOn().equals(MONDAY)
                && overdue.pendingBusinessDays() == 5));
    }

    @Test
    void theUploadCutoffIsTheStartOfTheFirstDayThatCanStillReachTheThreshold() {
        when(receipts.findPendingWithoutReviewAlertUploadedBefore(any())).thenReturn(List.of());

        alerterOn(THURSDAY, 3).alertOverdueReviews();

        verify(receipts).findPendingWithoutReviewAlertUploadedBefore(LocalDateTime.of(THURSDAY.minusDays(2), java.time.LocalTime.MIDNIGHT));
    }

    @Test
    void aThresholdBelowOneIsRejected() {
        assertThatThrownBy(() -> alerterOn(THURSDAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theJobRunsDailyInTheApplicationTimezoneUnderAClusterLock() throws NoSuchMethodException {
        var method = OverdueReceiptReviewAlertJob.class.getMethod("alertOverdueReviews");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 9 * * *");
        assertThat(scheduled.zone()).isEqualTo("${app.timezone}");
        assertThat(lock.name()).isEqualTo("OverdueReceiptReviewAlertJob");
    }

    @Test
    void theJobDelegatesToTheAlerter() {
        OverdueReceiptReviewAlerter alerter = mock(OverdueReceiptReviewAlerter.class);

        new OverdueReceiptReviewAlertJob(alerter).alertOverdueReviews();

        verify(alerter).alertOverdueReviews();
    }
}
