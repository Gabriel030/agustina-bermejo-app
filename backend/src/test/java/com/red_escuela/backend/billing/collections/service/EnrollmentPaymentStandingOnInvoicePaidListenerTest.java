package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentPaymentStandingOnInvoicePaidListenerTest {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2028, 3, 10);

    private final EnrollmentDebtFactsQuery factsQuery = mock(EnrollmentDebtFactsQuery.class);
    private final EnrollmentPaymentStandingService standingService = mock(EnrollmentPaymentStandingService.class);
    private final EnrollmentPaymentStandingOnInvoicePaidListener listener = new EnrollmentPaymentStandingOnInvoicePaidListener(factsQuery, standingService);
    private final InvoicePaidDomainEvent event = InvoicePaidDomainEvent.paidFor(55L, 9L, InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), BUSINESS_DATE);

    @Test
    @Tag("D-185")
    void theListenerIsAnApplicationModuleListenerSoThePublicationSurvivesACrashBetweenCommits() throws NoSuchMethodException {
        Method handler = EnrollmentPaymentStandingOnInvoicePaidListener.class.getMethod("reevaluateStandingOfPaidInvoice", InvoicePaidDomainEvent.class);

        assertThat(handler.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }

    @Test
    @Tag("D-185")
    void everyProgramAndCourseEnrollmentOfThePaidInvoiceIsReevaluatedOnTheBusinessDateOfThePayment() {
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.PROGRAM, 55L)).thenReturn(List.of(7L, 8L));
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.COURSE, 55L)).thenReturn(List.of(3L));

        listener.reevaluateStandingOfPaidInvoice(event);

        verify(standingService).reevaluateExistingStanding(EnrollmentKind.PROGRAM, 7L, BUSINESS_DATE);
        verify(standingService).reevaluateExistingStanding(EnrollmentKind.PROGRAM, 8L, BUSINESS_DATE);
        verify(standingService).reevaluateExistingStanding(EnrollmentKind.COURSE, 3L, BUSINESS_DATE);
    }

    @Test
    @Tag("D-185")
    void anInvoiceWithoutEnrollmentsReevaluatesNothing() {
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.PROGRAM, 55L)).thenReturn(List.of());
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.COURSE, 55L)).thenReturn(List.of());

        listener.reevaluateStandingOfPaidInvoice(event);

        verify(standingService, never()).reevaluateExistingStanding(any(), any(), any());
    }

    @Test
    @Tag("D-185")
    void aFailureOnOneEnrollmentDoesNotSkipTheOthersAndLeavesThePublicationPendingForReplay() {
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.PROGRAM, 55L)).thenReturn(List.of(7L, 8L));
        when(factsQuery.findEnrollmentIdsOfInvoice(EnrollmentKind.COURSE, 55L)).thenReturn(List.of(3L));
        doThrow(new IllegalStateException("database down")).when(standingService).reevaluateExistingStanding(EnrollmentKind.PROGRAM, 7L, BUSINESS_DATE);

        assertThatThrownBy(() -> listener.reevaluateStandingOfPaidInvoice(event)).isInstanceOf(IllegalStateException.class).hasMessageContaining("1 enrollment");

        verify(standingService, times(1)).reevaluateExistingStanding(EnrollmentKind.PROGRAM, 8L, BUSINESS_DATE);
        verify(standingService, times(1)).reevaluateExistingStanding(EnrollmentKind.COURSE, 3L, BUSINESS_DATE);
    }
}
