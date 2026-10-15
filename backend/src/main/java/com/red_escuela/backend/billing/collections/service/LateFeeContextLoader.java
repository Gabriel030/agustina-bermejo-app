package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
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
import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
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
import java.util.function.Predicate;

@Component
public class LateFeeContextLoader {
    private static final Set<InvoicePaymentStatus> STATUSES_WITH_LATE_FEE_CANDIDATES = Set.of(InvoicePaymentStatus.OVERDUE, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, InvoicePaymentStatus.PAID);

    private final LatePaymentVersionResolver versionResolver;
    private final EnrollmentLifecycleQuery enrollmentLifecycle;
    private final LateFeeCalculationStrategy calculationStrategy;
    private final InvoiceSettlementQueryService settlementQuery;
    private final LateFeeAccrualPlanner planner = new LateFeeAccrualPlanner();

    public LateFeeContextLoader(LatePaymentVersionResolver versionResolver, EnrollmentLifecycleQuery enrollmentLifecycle,
                                LateFeeCalculationStrategy calculationStrategy, InvoiceSettlementQueryService settlementQuery) {
        this.versionResolver = versionResolver;
        this.enrollmentLifecycle = enrollmentLifecycle;
        this.calculationStrategy = calculationStrategy;
        this.settlementQuery = settlementQuery;
    }

    public Optional<ResolvedLateFeeCutoff> resolveCutoff(Invoice origin, LocalDate businessDate) {
        return switch (origin.getPaymentStatus()) {
            case OVERDUE, PAYMENT_UNDER_REVIEW -> Optional.of(new ResolvedLateFeeCutoff(LateFeeCutoff.closingOfMonthBefore(businessDate), Optional.empty()));
            case PAID -> settlementQuery.findCurrentSettlementOfInvoice(origin.getId())
                    .map(settlement -> new ResolvedLateFeeCutoff(LateFeeCutoff.settledOn(settlement.paymentDate()), Optional.of(settlement.allocationId())));
            default -> Optional.empty();
        };
    }

    public List<LateFeeIssuingContext> loadMissingPeriodContexts(Invoice origin, List<LateFeeAccrualPeriod> coveredPeriods, LocalDate businessDate,
                                                                    LatePaymentTimeline timeline) {
        if (origin.getDocumentStatus() != InvoiceDocumentStatus.ISSUED || !STATUSES_WITH_LATE_FEE_CANDIDATES.contains(origin.getPaymentStatus())) {
            return List.of();
        }
        Optional<ResolvedLateFeeCutoff> resolvedCutoff = resolveCutoff(origin, businessDate);
        if (resolvedCutoff.isEmpty()) {
            return List.of();
        }
        LateFeeCutoff cutoff = resolvedCutoff.get().cutoff();
        Optional<LatePaymentVersion> dueDateVersion = versionResolver.findVersionInEffectOnOrFirst(origin.getDueDate(), origin.getId());
        if (dueDateVersion.isEmpty()) {
            return List.of();
        }
        LatePaymentTerms dueDateTerms = dueDateVersion.get().terms();
        DailyLateFeeSchedule schedule = new DailyLateFeeSchedule(origin.getDueDate(), dueDateTerms.gracePeriodDays());
        if (cutoff.kind() == LateFeeCutoffKind.MONTH_CLOSE && !schedule.isPastGraceOn(businessDate)) {
            return List.of();
        }
        LateFeeOriginFacts facts = originFactsOf(origin);
        Optional<LocalDate> enrollmentEndDate = findEnrollmentEndDate(facts);
        boolean frozenByRuleOfTheEndDate = enrollmentEndDate.map(end -> timeline.termsOn(end, dueDateTerms).freezeLateFeesOnWithdrawal()).orElse(false);
        Predicate<LocalDate> frozenOn = day -> frozenByRuleOfTheEndDate && enrollmentEndDate.map(day::isAfter).orElse(false);
        List<PlannedLateFeePeriod> missing = planner.planMissingPeriods(schedule, coveredPeriods, cutoff, day -> timeline.termsOn(day, dueDateTerms).lateFeeChargingEnabled(), frozenOn);
        return missing.stream().map(planned -> new LateFeeIssuingContext(facts, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(facts), planned,
                dueDateVersion.get().versionId(), dueDateTerms, frozenOn, enrollmentEndDate,
                calculationStrategy.calculateLateFee(facts.finalLinePrice(), dueDateTerms.dailyLateFeeRate(), planned.chargedDays()), businessDate,
                resolvedCutoff.get().settlementAllocationId())).toList();
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
