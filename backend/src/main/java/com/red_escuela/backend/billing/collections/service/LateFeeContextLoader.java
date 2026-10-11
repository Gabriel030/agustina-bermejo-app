package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeCalculationStrategy;
import com.red_escuela.backend.billing.collections.domain.DailyLateFeeSchedule;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPlanner;
import com.red_escuela.backend.billing.collections.domain.LateFeeCutoff;
import com.red_escuela.backend.billing.collections.domain.PlannedLateFeePeriod;
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
import java.util.function.Function;

@Component
public class LateFeeContextLoader {
    private static final Set<InvoicePaymentStatus> STATUSES_WITH_LATE_FEE_CANDIDATES = Set.of(InvoicePaymentStatus.OVERDUE, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

    private final LatePaymentVersionResolver versionResolver;
    private final EnrollmentLifecycleQuery enrollmentLifecycle;
    private final LateFeeCalculationStrategy calculationStrategy;
    private final LateFeeAccrualPlanner planner = new LateFeeAccrualPlanner();

    public LateFeeContextLoader(LatePaymentVersionResolver versionResolver, EnrollmentLifecycleQuery enrollmentLifecycle,
                                LateFeeCalculationStrategy calculationStrategy) {
        this.versionResolver = versionResolver;
        this.enrollmentLifecycle = enrollmentLifecycle;
        this.calculationStrategy = calculationStrategy;
    }

    public List<LateFeeIssuingContext> loadMissingPeriodContexts(Invoice origin, List<LateFeeAccrualPeriod> coveredPeriods, LocalDate businessDate) {
        if (origin.getDocumentStatus() != InvoiceDocumentStatus.ISSUED || !STATUSES_WITH_LATE_FEE_CANDIDATES.contains(origin.getPaymentStatus())) {
            return List.of();
        }
        Optional<LatePaymentVersion> dueDateVersion = versionResolver.findVersionInEffectOnOrFirst(origin.getDueDate(), origin.getId());
        if (dueDateVersion.isEmpty()) {
            return List.of();
        }
        LatePaymentTerms dueDateTerms = dueDateVersion.get().terms();
        DailyLateFeeSchedule schedule = new DailyLateFeeSchedule(origin.getDueDate(), dueDateTerms.gracePeriodDays());
        if (!schedule.isPastGraceOn(businessDate)) {
            return List.of();
        }
        LateFeeCutoff cutoff = LateFeeCutoff.closingOfMonthBefore(businessDate);
        LateFeeOriginFacts facts = originFactsOf(origin);
        Optional<LocalDate> enrollmentEndDate = findEnrollmentEndDate(facts);
        Function<LocalDate, LatePaymentTerms> termsOn = versionResolver.termsInEffectUpTo(cutoff.lastDay(), dueDateTerms);
        Optional<LocalDate> frozenAfter = termsOn.apply(cutoff.lastDay()).freezeLateFeesOnWithdrawal() ? enrollmentEndDate : Optional.empty();
        List<PlannedLateFeePeriod> missing = planner.planMissingPeriods(schedule, coveredPeriods, cutoff, day -> termsOn.apply(day).lateFeeChargingEnabled(), frozenAfter);
        return missing.stream().map(planned -> new LateFeeIssuingContext(facts, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(facts), planned,
                dueDateVersion.get().versionId(), dueDateTerms, termsOn.apply(cutoff.lastDay()), enrollmentEndDate,
                calculationStrategy.calculateLateFee(facts.finalLinePrice(), dueDateTerms.dailyLateFeeRate(), planned.chargedDays()), businessDate,
                Optional.empty())).toList();
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
