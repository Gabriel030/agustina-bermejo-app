package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class PendingTransferReviewRelease {
    private static final Logger log = LoggerFactory.getLogger(PendingTransferReviewRelease.class);

    private final BillingConfigurationReader configurationReader;
    private final ApplicationEventPublisher eventPublisher;

    PendingTransferReviewRelease(BillingConfigurationReader configurationReader, ApplicationEventPublisher eventPublisher) {
        this.configurationReader = configurationReader;
        this.eventPublisher = eventPublisher;
    }

    List<Long> releaseProtectedItemsStillUnderReview(List<PaymentCheckoutItem> items, Map<Long, Invoice> invoicesById, LocalDate decisionDate, StatusChangeActor actor,
                                                     String reason) {
        List<Long> released = new ArrayList<>();
        for (PaymentCheckoutItem item : items) {
            Invoice invoice = invoicesById.get(item.getInvoiceId());
            if (!item.isMarkedUnderReview() || invoice == null || invoice.getPaymentStatus() != InvoicePaymentStatus.PAYMENT_UNDER_REVIEW) {
                continue;
            }
            invoice.releasePaymentReview(decisionDate, gracePeriodDaysOf(invoice), actor, reason);
            invoice.pullRecordedDomainEvents().forEach(eventPublisher::publishEvent);
            released.add(invoice.getId());
        }
        return released;
    }

    private int gracePeriodDaysOf(Invoice invoice) {
        Optional<LatePaymentTerms> terms = configurationReader.findLatePaymentSettingsEffectiveOn(invoice.getDueDate())
                .or(configurationReader::findFirstLatePaymentSettings);
        if (terms.isEmpty()) {
            log.warn("Factura id={} sin versión de mora aplicable: se libera la revisión sin días de gracia", invoice.getId());
            return 0;
        }
        return terms.get().gracePeriodDays();
    }
}
