package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.api.AdvanceEnrollmentFeeView;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceEnrollmentFeeFacts;
import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeCalendar;
import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.service.AdvanceEnrollmentFeePriceFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Component
public class AdvanceEnrollmentFeeAvailabilityReader {
    private static final Logger log = LoggerFactory.getLogger(AdvanceEnrollmentFeeAvailabilityReader.class);
    private static final int FIRST_SLOT = 1;

    private final PolicySet<AdvanceEnrollmentFeeFacts> eligibilityPolicySet;
    private final AdvanceEnrollmentFeeCalendarReader calendarReader;
    private final AdvanceEnrollmentFeePriceFinder priceFinder;
    private final StudentOverdueDebtFinder overdueDebtFinder;
    private final Clock clock;

    public AdvanceEnrollmentFeeAvailabilityReader(PolicySet<AdvanceEnrollmentFeeFacts> advanceEnrollmentFeeEligibilityPolicySet, AdvanceEnrollmentFeeCalendarReader calendarReader,
                                                  AdvanceEnrollmentFeePriceFinder priceFinder, StudentOverdueDebtFinder overdueDebtFinder, Clock clock) {
        this.eligibilityPolicySet = advanceEnrollmentFeeEligibilityPolicySet;
        this.calendarReader = calendarReader;
        this.priceFinder = priceFinder;
        this.overdueDebtFinder = overdueDebtFinder;
        this.clock = clock;
    }

    public Optional<AdvanceEnrollmentFeeView> availabilityOfNextYearFirstSlot(ProgramEnrollment enrollment, ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot,
                                                                                      YearMonth currentMonth) {
        return Optional.of(cycle).filter(candidate -> slot == FIRST_SLOT && candidate.start().isAfter(currentMonth))
                .map(nextCycle -> availabilityOf(enrollment, facts, nextCycle));
    }

    public AdvanceEnrollmentFeeView availabilityOf(ProgramEnrollment enrollment, ProgramInvoicePlanningFacts facts, YearCycle nextCycle) {
        LocalDate today = LocalDate.now(clock);
        Optional<AdvanceEnrollmentFeeCalendar> calendar = calendarReader.calendarOf(nextCycle.template());
        Optional<PriceVersion> price = nextCycle.template().termYear() == null ? Optional.empty()
                : priceFinder.findLiveVersion(nextCycle.template().programId(), nextCycle.template().termYear());
        Optional<IssuedEnrollmentFee> issued = facts.feeOf(nextCycle, FIRST_SLOT);
        AdvanceEnrollmentFeeFacts eligibilityFacts = new AdvanceEnrollmentFeeFacts(today, calendar.isPresent() && price.isPresent(),
                enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED, issued.isPresent(),
                overdueDebtFinder.hasOverdueDebt(enrollment.getStudent().getId(), today),
                calendar.map(AdvanceEnrollmentFeeCalendar::advanceOpensOn).orElse(today), calendar.map(AdvanceEnrollmentFeeCalendar::windowClosesOn).orElse(today));
        Optional<PolicyVerdict> denial = eligibilityPolicySet.evaluateRulesUntilFirstViolation(eligibilityFacts).firstDenial();
        denial.ifPresent(verdict -> log.info("Matrícula anticipada no disponible: inscripcion={}, policy={}, code={}", enrollment.getId(), verdict.policy(), verdict.code()));
        Money amount = price.map(version -> version.finalAmount().value()).orElse(null);
        return new AdvanceEnrollmentFeeView(denial.isEmpty(), denial.map(PolicyVerdict::code).orElse(AdvanceEnrollmentFeeView.AVAILABLE_CODE),
                calendar.map(AdvanceEnrollmentFeeCalendar::advanceOpensOn).orElse(null), amount, calendar.map(AdvanceEnrollmentFeeCalendar::windowClosesOn).orElse(null),
                issued.map(IssuedEnrollmentFee::invoiceId).orElse(null));
    }
}
