package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-529")
class AdvanceEnrollmentFeeExpirerTest {
    private static final LocalDate CLOSING_DAY = LocalDate.of(2028, 2, 20);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final AdvanceEnrollmentFeeCalendarReader calendars = mock(AdvanceEnrollmentFeeCalendarReader.class);
    private final AdvanceEnrollmentFeeExpiration expiration = mock(AdvanceEnrollmentFeeExpiration.class);
    private final AdvanceEnrollmentFeeExpirer expirer = new AdvanceEnrollmentFeeExpirer(invoices, calendars, expiration);

    @Test
    void aTemplateWhoseWindowClosesTodayOrLaterIsNotConsulted() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L));
        when(calendars.windowClosesOnOfTemplate(7L)).thenReturn(Optional.of(CLOSING_DAY));

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(CLOSING_DAY);

        assertThat(result.candidates()).isZero();
        verify(invoices, never()).findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(anyLong());
        verify(expiration, never()).cancelWhenWindowClosedAndUnpaid(anyLong());
    }

    @Test
    void aTemplateWithoutAnEffectiveCalendarIsLeftAlone() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L));
        when(calendars.windowClosesOnOfTemplate(7L)).thenReturn(Optional.empty());

        assertThat(expirer.expireFeesWhoseWindowClosedBefore(CLOSING_DAY.plusDays(1)).candidates()).isZero();
        verify(expiration, never()).cancelWhenWindowClosedAndUnpaid(anyLong());
    }

    @Test
    void aFailingInvoiceIsReportedAndDoesNotStopTheOthersFromExpiringEvenInAnotherTemplate() {
        when(invoices.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()).thenReturn(List.of(7L, 8L));
        when(calendars.windowClosesOnOfTemplate(7L)).thenReturn(Optional.of(CLOSING_DAY));
        when(calendars.windowClosesOnOfTemplate(8L)).thenReturn(Optional.of(CLOSING_DAY.minusDays(30)));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(7L)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(invoices.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(8L)).thenReturn(List.of(5L));
        when(expiration.cancelWhenWindowClosedAndUnpaid(1L)).thenReturn(true);
        when(expiration.cancelWhenWindowClosedAndUnpaid(2L)).thenThrow(new IllegalStateException("boom"));
        when(expiration.cancelWhenWindowClosedAndUnpaid(3L)).thenReturn(false);
        when(expiration.cancelWhenWindowClosedAndUnpaid(4L)).thenReturn(true);
        when(expiration.cancelWhenWindowClosedAndUnpaid(5L)).thenReturn(true);

        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(CLOSING_DAY.plusDays(1));

        assertThat(result.expired()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failedInvoiceIds()).containsExactly(2L);
        assertThat(result.candidates()).isEqualTo(5);
    }
}
