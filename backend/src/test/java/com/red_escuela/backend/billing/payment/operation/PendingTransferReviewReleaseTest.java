package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReviewReleasedEvent;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.domain.PlannedCheckoutItem;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issue;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.monthlyDraft;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-153")
@Tag("D-154")
class PendingTransferReviewReleaseTest {

    private static final LocalDate DUE = LocalDate.of(2028, 3, 10);

    private BillingConfigurationReader configurationReader;
    private ApplicationEventPublisher eventPublisher;
    private PendingTransferReviewRelease release;

    @BeforeEach
    void createTheReleaseWithoutAnyLatePaymentTerms() {
        configurationReader = mock(BillingConfigurationReader.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        when(configurationReader.findLatePaymentSettingsEffectiveOn(any())).thenReturn(Optional.empty());
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(Optional.empty());
        release = new PendingTransferReviewRelease(configurationReader, eventPublisher);
    }

    private static LatePaymentTerms termsWithGraceDays(int graceDays) {
        return new LatePaymentTerms(graceDays, Percentage.ofFraction(BigDecimal.ZERO), 30, 2, true, 10, true, false);
    }

    private static Invoice underReview() {
        Invoice invoice = withId(monthlyDraft("80000.00"), 8L);
        issue(invoice, DUE.minusDays(10), DUE);
        invoice.markPaymentUnderReview(DUE, ACTOR, "comprobante");
        invoice.pullRecordedDomainEvents();
        return invoice;
    }

    private static PaymentCheckoutItem protectedItemOf(Invoice invoice) {
        return PaymentCheckoutItem.of(1L, 2L, new PlannedCheckoutItem(invoice.getId(), 1, ars("80000.00").value(), true, true));
    }

    private List<Long> releaseOn(Invoice invoice, LocalDate decisionDate) {
        return release.releaseProtectedItemsStillUnderReview(List.of(protectedItemOf(invoice)), Map.of(invoice.getId(), invoice), decisionDate, ACTOR, "motivo");
    }

    @Test
    void aReleaseOnTheLastGraceDayGoesBackToPending() {
        when(configurationReader.findLatePaymentSettingsEffectiveOn(DUE)).thenReturn(Optional.of(termsWithGraceDays(5)));
        Invoice invoice = underReview();

        releaseOn(invoice, DUE.plusDays(5));

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    void aReleaseOneDayAfterTheGracePeriodGoesToOverdue() {
        when(configurationReader.findLatePaymentSettingsEffectiveOn(DUE)).thenReturn(Optional.of(termsWithGraceDays(5)));
        Invoice invoice = underReview();

        releaseOn(invoice, DUE.plusDays(6));

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).containsExactly(new InvoicePaymentReviewReleasedEvent(8L, DUE.plusDays(6)),
                new InvoiceMarkedAsOverdueEvent(8L, DUE, DUE.plusDays(6)));
    }

    @Test
    void theGracePeriodOfTheFirstVersionAppliesWhenNoneWasInEffectOnTheDueDate() {
        when(configurationReader.findFirstLatePaymentSettings()).thenReturn(Optional.of(termsWithGraceDays(5)));
        Invoice invoice = underReview();

        releaseOn(invoice, DUE.plusDays(5));

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    void withoutLatePaymentTermsTheInvoiceGoesBackToPendingEvenAfterTheDueDateLikeTheOverdueSweep() {
        Invoice invoice = underReview();

        List<Long> released = releaseOn(invoice, DUE.plusDays(40));

        assertThat(released).containsExactly(8L);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isEqualTo(new InvoicePaymentReviewReleasedEvent(8L, DUE.plusDays(40)));
    }

    @Test
    void anInvoiceNoLongerUnderReviewIsNotReleased() {
        when(configurationReader.findLatePaymentSettingsEffectiveOn(DUE)).thenReturn(Optional.of(termsWithGraceDays(5)));
        Invoice invoice = underReview();
        releaseOn(invoice, DUE);

        List<Long> secondRelease = releaseOn(invoice, DUE);

        assertThat(secondRelease).isEmpty();
    }
}
