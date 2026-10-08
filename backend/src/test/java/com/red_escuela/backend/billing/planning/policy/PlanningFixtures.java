package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlanningFixtures {

    private PlanningFixtures() {
    }

    static ProgramYearBillingTemplate template(long id, int year, int installments, int count, int first, Integer second) {
        Term term = new Term();
        term.setYear(year);
        term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(year, 12, 31, 23, 59));
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(id);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(installments);
        template.configureEnrollmentFees(count, first, second);
        return template;
    }

    static ProgramInvoicePlanningFacts facts(ProgramYearBillingTemplate template, YearMonth month, LocalDate today, boolean paused, boolean onlyFinals,
                                             YearMonth entry, PaymentPlanCoverage coverage, List<IssuedEnrollmentFee> fees, Set<YearMonth> recurring,
                                             Map<String, SlotWindow> windows) {
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(template, month.getYear())), null);
        return new ProgramInvoicePlanningFacts(1L, month, today, true, false, false, paused, onlyFinals, entry, coverage, true, cycles, fees, recurring,
                windows, null, null, null);
    }

    static ProgramInvoicePlanningFacts facts(ProgramYearBillingTemplate template, YearMonth month, boolean paused, boolean onlyFinals) {
        return facts(template, month, month.atDay(1), paused, onlyFinals, null, PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), List.of(), Set.of(), Map.of());
    }
}
