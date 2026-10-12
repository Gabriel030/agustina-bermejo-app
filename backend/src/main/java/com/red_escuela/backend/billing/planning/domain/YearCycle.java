package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.time.YearMonth;

public final class YearCycle {
    private static final int PREVIOUS_YEAR_THRESHOLD_MONTHS = 6;
    private static final int LECTIVE_YEAR_FIRST_MONTH = 3;

    private final ProgramYearBillingTemplate template;
    private final YearBillingTerms yearTerms;
    private final YearMonth start;
    private final YearMonth end;
    private final YearMonth termStart;
    private final int baseYear;
    private final YearMonth coverageStart;

    private YearCycle(ProgramYearBillingTemplate template, YearBillingTerms yearTerms, YearMonth start, YearMonth end, YearMonth termStart, int baseYear, YearMonth coverageStart) {
        this.coverageStart = coverageStart;
        this.template = template;
        this.yearTerms = yearTerms;
        this.start = start;
        this.end = end;
        this.termStart = termStart;
        this.baseYear = baseYear;
    }

    public static YearCycle of(ProgramYearBillingTemplate template, YearBillingTerms yearTerms, int fallbackYear) {
        if (template.termStartDate() == null || template.termEndDate() == null) {
            int year = template.termYear() != null ? template.termYear() : fallbackYear;
            YearMonth first = YearMonth.of(year, 1);
            return new YearCycle(template, yearTerms, first, YearMonth.of(year, 12), YearMonth.of(year, LECTIVE_YEAR_FIRST_MONTH), year, first);
        }
        YearMonth termStart = YearMonth.from(template.termStartDate());
        YearMonth termEnd = YearMonth.from(template.termEndDate());
        YearMonth firstFee = firstFeePeriod(yearTerms, termStart);
        YearMonth start = firstFee.isBefore(termStart) ? firstFee : termStart;
        return new YearCycle(template, yearTerms, start, termEnd, termStart, termStart.getYear(), start);
    }

    private static YearMonth firstFeePeriod(YearBillingTerms yearTerms, YearMonth termStart) {
        int month = yearTerms.firstEnrollmentFeeMonth();
        boolean belongsToPreviousYear = month - termStart.getMonthValue() >= PREVIOUS_YEAR_THRESHOLD_MONTHS;
        return YearMonth.of(belongsToPreviousYear ? termStart.getYear() - 1 : termStart.getYear(), month);
    }

    public YearCycle withCoverageStart(YearMonth lowerBound) {
        return new YearCycle(template, yearTerms, start, end, termStart, baseYear, lowerBound.isBefore(start) ? lowerBound : start);
    }

    public ProgramYearBillingTemplate template() {
        return template;
    }

    public YearBillingTerms yearTerms() {
        return yearTerms;
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

    public LectiveYearBillingTerms terms() {
        return LectiveYearBillingTerms.of(yearTerms);
    }

    public int slotCount() {
        return yearTerms.enrollmentFeeCount();
    }

    public YearMonth slotPeriod(int slot) {
        if (slot == 1) {
            if (template.termStartDate() == null || template.termEndDate() == null) {
                return YearMonth.of(baseYear, yearTerms.firstEnrollmentFeeMonth());
            }
            return firstFeePeriod(yearTerms, termStart);
        }
        return YearMonth.of(baseYear, yearTerms.secondEnrollmentFeeMonth());
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
