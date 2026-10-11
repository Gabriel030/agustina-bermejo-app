package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class LateFeeAccrualPlanner {

    public List<PlannedLateFeePeriod> planMissingPeriods(DailyLateFeeSchedule schedule, Collection<LateFeeAccrualPeriod> coveredPeriods, LateFeeCutoff cutoff,
                                                         Predicate<LocalDate> chargingEnabledOn, Optional<LocalDate> frozenAfter) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(coveredPeriods, "coveredPeriods");
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(chargingEnabledOn, "chargingEnabledOn");
        Objects.requireNonNull(frozenAfter, "frozenAfter");
        if (cutoff.kind() == LateFeeCutoffKind.SETTLEMENT && !schedule.isPastGraceOn(cutoff.lastDay())) {
            return List.of();
        }
        LocalDate from = coveredPeriods.stream().map(LateFeeAccrualPeriod::lastDay).max(Comparator.naturalOrder()).map(last -> last.plusDays(1))
                .filter(next -> next.isAfter(schedule.firstChargeableDay())).orElse(schedule.firstChargeableDay());
        LocalDate to = frozenAfter.filter(end -> end.isBefore(cutoff.lastDay())).orElse(cutoff.lastDay());
        List<PlannedLateFeePeriod> planned = new ArrayList<>();
        for (LateFeeAccrualPeriod period : schedule.splitByCalendarMonth(from, to)) {
            int enabledDays = countEnabledDays(period, chargingEnabledOn);
            if (enabledDays > 0) {
                planned.add(new PlannedLateFeePeriod(period, enabledDays, cutoff.kind()));
            }
        }
        return planned;
    }

    private static int countEnabledDays(LateFeeAccrualPeriod period, Predicate<LocalDate> chargingEnabledOn) {
        int enabled = 0;
        for (LocalDate day = period.firstDay(); !day.isAfter(period.lastDay()); day = day.plusDays(1)) {
            if (chargingEnabledOn.test(day)) {
                enabled++;
            }
        }
        return enabled;
    }
}
