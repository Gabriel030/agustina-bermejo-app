package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeCalculationStrategy;
import com.red_escuela.backend.billing.collections.domain.LateFeeMonth;
import com.red_escuela.backend.billing.collections.domain.LateFeeSchedule;
import com.red_escuela.backend.billing.collections.policy.LateFeeDebitNoteDraftFacts;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.policy.LateFeeOriginFacts;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.enrollment.usecase.EnrollmentLifecycleQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LateFeeContextLoader {
    private static final Set<InvoicePaymentStatus> STATUSES_WITH_LATE_FEE_CANDIDATES = Set.of(InvoicePaymentStatus.OVERDUE, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

    private final LatePaymentVersionResolver versionResolver;
    private final EnrollmentLifecycleQuery enrollmentLifecycle;
    private final LateFeeCalculationStrategy calculationStrategy;

    public LateFeeContextLoader(LatePaymentVersionResolver versionResolver, EnrollmentLifecycleQuery enrollmentLifecycle,
                                LateFeeCalculationStrategy calculationStrategy) {
        this.versionResolver = versionResolver;
        this.enrollmentLifecycle = enrollmentLifecycle;
        this.calculationStrategy = calculationStrategy;
    }

    public List<LateFeeIssuingContext> loadMissingMonthContexts(Invoice origin, Set<Integer> alreadyChargedOrdinals, LocalDate businessDate) {
        if (origin.getDocumentStatus() != InvoiceDocumentStatus.ISSUED || !STATUSES_WITH_LATE_FEE_CANDIDATES.contains(origin.getPaymentStatus())) {
            return List.of();
        }
        Optional<LatePaymentVersion> dueDateVersion = versionResolver.findVersionInEffectOnOrFirst(origin.getDueDate(), origin.getId());
        if (dueDateVersion.isEmpty()) {
            return List.of();
        }
        LatePaymentTerms dueDateTerms = dueDateVersion.get().terms();
        List<LateFeeMonth> missingMonths = new LateFeeSchedule(origin.getDueDate(), dueDateTerms.gracePeriodDays()).monthsWithFactDateOnOrBefore(businessDate).stream()
                .filter(month -> !alreadyChargedOrdinals.contains(month.ordinal())).toList();
        if (missingMonths.isEmpty()) {
            return List.of();
        }
        LateFeeOriginFacts facts = originFactsOf(origin);
        Optional<LocalDate> enrollmentEndDate = findEnrollmentEndDate(facts);
        return missingMonths.stream().map(month -> new LateFeeIssuingContext(facts, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(facts), month,
                dueDateVersion.get().versionId(), dueDateTerms, termsInEffectOn(month.factDate(), origin, dueDateTerms), enrollmentEndDate,
                calculationStrategy.calculateLateFee(facts.finalLinePrice(), dueDateTerms.monthlyLateFeeRate()), businessDate)).toList();
    }

    private LatePaymentTerms termsInEffectOn(LocalDate factDate, Invoice origin, LatePaymentTerms fallback) {
        return versionResolver.findVersionInEffectOnOrFirst(factDate, origin.getId()).map(LatePaymentVersion::terms).orElse(fallback);
    }

    private Optional<LocalDate> findEnrollmentEndDate(LateFeeOriginFacts facts) {
        if (facts.programEnrollmentId() != null) {
            return enrollmentLifecycle.findProgramEnrollmentEndDate(facts.programEnrollmentId());
        }
        if (facts.courseEnrollmentId() != null) {
            return enrollmentLifecycle.findCourseEnrollmentEndDate(facts.courseEnrollmentId());
        }
        return Optional.empty();
    }

    private LateFeeOriginFacts originFactsOf(Invoice origin) {
        InvoiceLine line = origin.getPrimaryLine();
        return new LateFeeOriginFacts(origin.getId(), origin.getInvoiceNumber().value(), origin.getStudentCurrentAccountId(), origin.getDocumentType(),
                origin.getInvoiceType(), origin.getPaymentStatus(), origin.getDueDate(), origin.getCurrency(), line.getAmount(), origin.getStudentSnapshot(),
                line.getPeriod(), line.getProgramEnrollmentId(), line.getCourseEnrollmentId());
    }
}
