package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.policy.InvoiceIsPastDueDatePolicy;
import com.red_escuela.backend.billing.collections.policy.OverdueDetectionContext;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceLockingService;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverdueInvoiceTransitionServiceTest {
    private static final LocalDate DUE_DATE = LocalDate.of(2020, 3, 12);

    private final InvoiceLockingService invoiceLockingService = mock(InvoiceLockingService.class);
    private final BillingConfigurationReader reader = mock(BillingConfigurationReader.class);
    private final Invoice invoice = mock(Invoice.class);
    private final OverdueInvoiceTransitionService service = new OverdueInvoiceTransitionService(invoiceLockingService, reader,
            PolicySet.<OverdueDetectionContext>named("OVERDUE_DETECTION", List.of(new InvoiceIsPastDueDatePolicy())), mock(ApplicationEventPublisher.class));

    private LatePaymentTerms termsWithGrace(int graceDays) {
        return new LatePaymentTerms(graceDays, Percentage.ofFraction(BigDecimal.ZERO), 0, 1, true, 10, true, false);
    }

    private void arrangeInvoiceDueBeforeEveryVersion() {
        when(invoice.getPaymentStatus()).thenReturn(InvoicePaymentStatus.PENDING);
        when(invoice.getDueDate()).thenReturn(DUE_DATE);
        when(invoiceLockingService.lockInvoiceForUpdate(7L)).thenReturn(invoice);
        when(reader.findLatePaymentSettingsEffectiveOn(DUE_DATE)).thenReturn(Optional.empty());
    }

    @Test
    @Tag("D-288")
    void anInstallmentDueBeforeTheFirstVersionUsesTheFirstVersion() {
        arrangeInvoiceDueBeforeEveryVersion();
        when(reader.findFirstLatePaymentSettings()).thenReturn(Optional.of(termsWithGrace(5)));

        assertThat(service.markAsOverdueWhenPastDueDate(7L, DUE_DATE.plusDays(5))).isEqualTo(OverdueTransitionOutcome.NOT_YET_OVERDUE);
        assertThat(service.markAsOverdueWhenPastDueDate(7L, DUE_DATE.plusDays(6))).isEqualTo(OverdueTransitionOutcome.MARKED_AS_OVERDUE);
        verify(invoice).markInvoiceAsOverdue(any(), any());
    }

    @Test
    @Tag("D-288")
    void withoutAnyLatePaymentVersionTheInvoiceIsLeftUntouched() {
        arrangeInvoiceDueBeforeEveryVersion();
        when(reader.findFirstLatePaymentSettings()).thenReturn(Optional.empty());

        assertThat(service.markAsOverdueWhenPastDueDate(7L, DUE_DATE.plusYears(1))).isEqualTo(OverdueTransitionOutcome.SKIPPED_WITHOUT_LATE_PAYMENT_TERMS);
        verify(invoice, never()).markInvoiceAsOverdue(any(), any());
    }
}
