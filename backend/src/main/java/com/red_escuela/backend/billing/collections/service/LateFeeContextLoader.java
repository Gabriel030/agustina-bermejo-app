package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
import com.red_escuela.backend.billing.collections.domain.LateFeeCalculationStrategy;
import com.red_escuela.backend.billing.collections.domain.DailyLateFeeSchedule;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPlanner;
import com.red_escuela.backend.billing.collections.domain.LateFeeCutoff;
import com.red_escuela.backend.billing.collections.domain.LateFeeFreeze;
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
    private final LateFeeFreezeResolver freezeResolver;
    private final LateFeeCalculationStrategy calculationStrategy;
    private final InvoiceSettlementQueryService settlementQuery;
    private final LateFeeAccrualPlanner planner = new LateFeeAccrualPlanner();

    public LateFeeContextLoader(LatePaymentVersionResolver versionResolver, LateFeeFreezeResolver freezeResolver,
                                LateFeeCalculationStrategy calculationStrategy, InvoiceSettlementQueryService settlementQuery) {
        this.versionResolver = versionResolver;
        this.freezeResolver = freezeResolver;
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
        ChargingDays chargingDays = resolveChargingDays(facts, dueDateTerms, timeline);
        LateFeeFreeze freeze = chargingDays.freeze();
        Predicate<LocalDate> frozenOn = freeze.frozenOn();
        List<PlannedLateFeePeriod> missing = planner.planMissingPeriods(schedule, coveredPeriods, cutoff, chargingDays.enabledOn(), frozenOn);
        return missing.stream().map(planned -> new LateFeeIssuingContext(facts, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(facts), planned,
                dueDateVersion.get().versionId(), dueDateTerms, frozenOn, freeze.enrollmentEndDate(),
                calculationStrategy.calculateLateFee(facts.finalLinePrice(), dueDateTerms.dailyLateFeeRate(), planned.chargedDays()), businessDate,
                resolvedCutoff.get().settlementAllocationId())).toList();
    }

    private ChargingDays resolveChargingDays(LateFeeOriginFacts facts, LatePaymentTerms dueDateTerms, LatePaymentTimeline timeline) {
        LateFeeFreeze freeze = freezeResolver.resolveFreezeOfEnrollment(facts.programEnrollmentId(), facts.courseEnrollmentId(), dueDateTerms, timeline);
        return new ChargingDays(freeze, day -> timeline.termsOn(day, dueDateTerms).lateFeeChargingEnabled());
    }

    private record ChargingDays(LateFeeFreeze freeze, Predicate<LocalDate> enabledOn) {
    }

    private LateFeeOriginFacts originFactsOf(Invoice origin) {
        InvoiceLine line = origin.getPrimaryLine();
        return new LateFeeOriginFacts(origin.getId(), origin.getInvoiceNumber().value(), origin.getStudentCurrentAccountId(), origin.getDocumentType(),
                origin.getInvoiceType(), origin.getPaymentStatus(), origin.getDueDate(), origin.getCurrency(), line.getAmount(), origin.getStudentSnapshot(),
                line.getPeriod(), line.getProgramEnrollmentId(), line.getCourseEnrollmentId());
    }
}
