package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.policy.OverdueDetectionContext;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.SubjectLockSequence;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class OverdueInvoiceTransitionService {
    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceTransitionService.class);
    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "OverdueInvoiceDetection");

    private final SubjectLockSequence subjectLockSequence;
    private final BillingConfigurationReader configurationReader;
    private final PolicySet<OverdueDetectionContext> overdueDetectionPolicySet;
    private final ApplicationEventPublisher eventPublisher;

    public OverdueInvoiceTransitionService(SubjectLockSequence subjectLockSequence, BillingConfigurationReader configurationReader,
                                           PolicySet<OverdueDetectionContext> overdueDetectionPolicySet, ApplicationEventPublisher eventPublisher) {
        this.subjectLockSequence = subjectLockSequence;
        this.configurationReader = configurationReader;
        this.overdueDetectionPolicySet = overdueDetectionPolicySet;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OverdueTransitionOutcome markAsOverdueWhenPastDueDate(Long invoiceId, LocalDate today) {
        Invoice invoice = subjectLockSequence.lockInvoiceFollowingTheEnrollmentAndAccount(invoiceId);
        Optional<LatePaymentTerms> terms = resolveTermsInEffectOnDueDate(invoice);
        if (terms.isEmpty()) {
            log.warn("Factura id={} sin versión de mora aplicable: no se evalúa su vencimiento", invoiceId);
            return OverdueTransitionOutcome.SKIPPED_WITHOUT_LATE_PAYMENT_TERMS;
        }
        OverdueDetectionContext context = new OverdueDetectionContext(invoice.getPaymentStatus(), invoice.getDueDate(), terms.get().gracePeriodDays(), today);
        if (!overdueDetectionPolicySet.evaluateAllRules(context).allowed()) {
            return OverdueTransitionOutcome.NOT_YET_OVERDUE;
        }
        invoice.markInvoiceAsOverdue(today, ACTOR);
        invoice.pullRecordedDomainEvents().forEach(eventPublisher::publishEvent);
        return OverdueTransitionOutcome.MARKED_AS_OVERDUE;
    }

    private Optional<LatePaymentTerms> resolveTermsInEffectOnDueDate(Invoice invoice) {
        Optional<LatePaymentTerms> inEffect = configurationReader.findLatePaymentSettingsEffectiveOn(invoice.getDueDate());
        if (inEffect.isPresent()) {
            return inEffect;
        }
        Optional<LatePaymentTerms> first = configurationReader.findFirstLatePaymentSettings();
        first.ifPresent(terms -> log.warn("Factura id={} vence el {}, antes de la primera versión de mora: se usa la primera versión", invoice.getId(), invoice.getDueDate()));
        return first;
    }
}
