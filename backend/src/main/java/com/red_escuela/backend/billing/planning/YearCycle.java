package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.time.YearMonth;

public final class YearCycle {
    private static final int PREVIOUS_YEAR_THRESHOLD_MONTHS = 6;

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
            int year = term.getYear() != null ? term.getYear() : fallbackYear;
            YearMonth first = YearMonth.of(year, 1);
            return new YearCycle(template, first, YearMonth.of(year, 12), first, year, first);
        }
        YearMonth termStart = YearMonth.from(term.getStartDate());
        YearMonth termEnd = YearMonth.from(term.getEndDate());
        YearMonth firstFee = firstFeePeriod(template, termStart);
        YearMonth start = firstFee.isBefore(termStart) ? firstFee : termStart;
        return new YearCycle(template, start, termEnd, termStart, termStart.getYear(), start);
    }

    private static YearMonth firstFeePeriod(ProgramYearBillingTemplate template, YearMonth termStart) {
        int month = template.getFirstEnrollmentFeeMonth();
        boolean belongsToPreviousYear = month - termStart.getMonthValue() >= PREVIOUS_YEAR_THRESHOLD_MONTHS;
        return YearMonth.of(belongsToPreviousYear ? termStart.getYear() - 1 : termStart.getYear(), month);
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

    public boolean installmentRangeContains(YearMonth month) {
        return !month.isBefore(termStart) && !month.isAfter(end);
    }

    public int slotCount() {
        return template.getEnrollmentFeeCount();
    }

    public YearMonth slotPeriod(int slot) {
        if (slot == 1) {
            Term term = template.getTerm();
            if (term.getStartDate() == null || term.getEndDate() == null) {
                return YearMonth.of(baseYear, template.getFirstEnrollmentFeeMonth());
            }
            return firstFeePeriod(template, termStart);
        }
        return YearMonth.of(baseYear, template.getSecondEnrollmentFeeMonth());
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
