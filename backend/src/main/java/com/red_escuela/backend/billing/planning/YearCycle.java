package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.time.YearMonth;

public final class YearCycle {
    private final ProgramYearBillingTemplate template;
    private final YearMonth start;
    private final YearMonth end;
    private final YearMonth termStart;
    private final int baseYear;
    private final YearMonth coverageStart;

    private YearCycle(ProgramYearBillingTemplate template, YearMonth start, YearMonth end, YearMonth termStart, int baseYear, YearMonth coverageStart) {
        this.coverageStart = coverageStart;
        this.template = template;
        this.start = start;
        this.end = end;
        this.termStart = termStart;
        this.baseYear = baseYear;
    }

    public static YearCycle of(ProgramYearBillingTemplate template, int fallbackYear) {
        Term term = template.getTerm();
        if (term.getStartDate() == null || term.getEndDate() == null) {
            YearMonth first = YearMonth.of(fallbackYear, 1);
            return new YearCycle(template, first, YearMonth.of(fallbackYear, 12), first, fallbackYear, first);
        }
        YearMonth termStart = YearMonth.from(term.getStartDate());
        YearMonth termEnd = YearMonth.from(term.getEndDate());
        YearMonth firstFee = YearMonth.of(termStart.getYear(), template.getFirstEnrollmentFeeMonth());
        YearMonth start = firstFee.isBefore(termStart) ? firstFee : termStart;
        return new YearCycle(template, start, termEnd, termStart, termStart.getYear(), start);
    }

    public YearCycle withCoverageStart(YearMonth lowerBound) {
        return new YearCycle(template, start, end, termStart, baseYear, lowerBound.isBefore(start) ? lowerBound : start);
    }

    public ProgramYearBillingTemplate template() {
        return template;
    }

    public Long templateId() {
        return template.getId();
    }

    public YearMonth start() {
        return start;
    }

    public YearMonth end() {
        return end;
    }

    public YearMonth termStart() {
        return termStart;
    }

    public boolean contains(YearMonth month) {
        return !month.isBefore(start) && !month.isAfter(end);
    }

    public boolean termContains(YearMonth month) {
        return template.getTerm().contains(month);
    }

    public int slotCount() {
        return template.getEnrollmentFeeCount();
    }

    public YearMonth slotPeriod(int slot) {
        int month = slot == 1 ? template.getFirstEnrollmentFeeMonth() : template.getSecondEnrollmentFeeMonth();
        return YearMonth.of(baseYear, month);
    }

    public int slotOf(YearMonth month) {
        if (slotCount() == 1) {
            return 1;
        }
        return month.isBefore(slotPeriod(2)) ? 1 : 2;
    }

    public YearMonth slotFrom(int slot) {
        return slot == 1 ? coverageStart : slotPeriod(2);
    }

    public YearMonth slotTo(int slot) {
        if (slot == 1 && slotCount() == 2) {
            return slotPeriod(2).minusMonths(1);
        }
        return end;
    }

    public boolean slotContains(int slot, YearMonth period) {
        return !period.isBefore(slotFrom(slot)) && !period.isAfter(slotTo(slot));
    }
}
