package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpiryResult.Failure;
import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-529")
class AdvanceEnrollmentFeeExpirerTest {
    private static final LocalDate CLOSING_DAY = LocalDate.of(2028, 2, 20);
    private static final LocalDate NIGHT = CLOSING_DAY.plusDays(1);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final AdvanceEnrollmentFeeCalendarReader calendars = mock(AdvanceEnrollmentFeeCalendarReader.class);
    private final AdvanceEnrollmentFeeExpiration expiration = mock(AdvanceEnrollmentFeeExpiration.class);
    private final AdvanceEnrollmentFeeExpirer expirer = new AdvanceEnrollmentFeeExpirer(invoices, calendars, expiration);

    @Test
    void aTemplateWhoseWindowClosesTodayOrLaterIsNotConsulted() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L));
        when(calendars.windowClosesOnOfTemplate(7L, CLOSING_DAY)).thenReturn(Optional.of(CLOSING_DAY));

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(CLOSING_DAY);

        assertThat(result.candidates()).isZero();
        verify(invoices, never()).findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(anyLong());
        verify(expiration, never()).cancelWhenWindowClosedAndUnpaid(anyLong());
    }

    @Test
    void theCalendarIsReadAsOfTheBusinessDate() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L));
        when(calendars.windowClosesOnOfTemplate(7L, NIGHT)).thenReturn(Optional.of(CLOSING_DAY));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(7L)).thenReturn(List.of(1L));
        when(expiration.cancelWhenWindowClosedAndUnpaid(1L)).thenReturn(true);

        assertThat(expirer.expireFeesWhoseWindowClosedBefore(NIGHT).expired()).isEqualTo(1);
        verify(calendars).windowClosesOnOfTemplate(7L, NIGHT);
    }

    @Test
    void aTemplateWithoutAnEffectiveCalendarCountsItsInvoicesAsSkippedInsteadOfDroppingThemSilently() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L));
        when(calendars.windowClosesOnOfTemplate(7L, NIGHT)).thenReturn(Optional.empty());
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(7L)).thenReturn(List.of(1L, 2L));

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(NIGHT);

        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.candidates()).isEqualTo(2);
        verify(expiration, never()).cancelWhenWindowClosedAndUnpaid(anyLong());
    }

    @Test
    void aTemplateWhoseCalendarFailsIsReportedWithItsCauseAndTheOtherTemplatesAreStillProcessed() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L, 8L));
        when(calendars.windowClosesOnOfTemplate(7L, NIGHT)).thenThrow(new IllegalStateException("terms corrupted"));
        when(calendars.windowClosesOnOfTemplate(8L, NIGHT)).thenReturn(Optional.of(CLOSING_DAY));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(7L)).thenReturn(List.of(1L, 2L));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(8L)).thenReturn(List.of(3L));
        when(expiration.cancelWhenWindowClosedAndUnpaid(3L)).thenReturn(true);

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(NIGHT);

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failures()).extracting(Failure::invoiceId, Failure::code).containsExactly(
                org.assertj.core.api.Assertions.tuple(1L, AdvanceEnrollmentFeeExpirer.CALENDAR_FAILED), org.assertj.core.api.Assertions.tuple(2L, AdvanceEnrollmentFeeExpirer.CALENDAR_FAILED));
        assertThat(result.failures()).allSatisfy(failure -> assertThat(failure.message()).contains("IllegalStateException").contains("terms corrupted"));
        verify(expiration).cancelWhenWindowClosedAndUnpaid(3L);
        verify(expiration, never()).cancelWhenWindowClosedAndUnpaid(1L);
    }

    @Test
    void aFailingInvoiceKeepsItsCauseAndDoesNotStopTheOthersFromExpiringEvenInAnotherTemplate() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L, 8L));
        when(calendars.windowClosesOnOfTemplate(any(), any())).thenReturn(Optional.of(CLOSING_DAY));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(7L)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(8L)).thenReturn(List.of(5L));
        when(expiration.cancelWhenWindowClosedAndUnpaid(1L)).thenReturn(true);
        when(expiration.cancelWhenWindowClosedAndUnpaid(2L)).thenThrow(new IllegalStateException("boom"));
        when(expiration.cancelWhenWindowClosedAndUnpaid(3L)).thenReturn(false);
        when(expiration.cancelWhenWindowClosedAndUnpaid(4L)).thenReturn(true);
        when(expiration.cancelWhenWindowClosedAndUnpaid(5L)).thenReturn(true);

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(NIGHT);

        assertThat(result.expired()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.invoiceId()).isEqualTo(2L);
            assertThat(failure.code()).isEqualTo(AdvanceEnrollmentFeeExpirer.EXPIRY_FAILED);
            assertThat(failure.message()).isEqualTo("IllegalStateException: boom");
        });
        assertThat(result.candidates()).isEqualTo(5);
    }
}
