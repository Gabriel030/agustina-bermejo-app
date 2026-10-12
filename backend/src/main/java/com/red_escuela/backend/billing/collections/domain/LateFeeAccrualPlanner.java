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
                                                         Predicate<LocalDate> chargingEnabledOn, Predicate<LocalDate> frozenOn) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(coveredPeriods, "coveredPeriods");
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(chargingEnabledOn, "chargingEnabledOn");
        Objects.requireNonNull(frozenOn, "frozenOn");
        if (cutoff.kind() == LateFeeCutoffKind.SETTLEMENT && !schedule.isPastGraceOn(cutoff.lastDay())) {
            return List.of();
        }
        LocalDate from = coveredPeriods.stream().map(LateFeeAccrualPeriod::lastDay).max(Comparator.naturalOrder()).map(last -> last.plusDays(1))
                .filter(next -> next.isAfter(schedule.firstChargeableDay())).orElse(schedule.firstChargeableDay());
        List<PlannedLateFeePeriod> planned = new ArrayList<>();
        for (LateFeeAccrualPeriod month : schedule.splitByCalendarMonth(from, cutoff.lastDay())) {
            Optional<LateFeeAccrualPeriod> unfrozen = trimFrozenEdges(month, frozenOn);
            if (unfrozen.isEmpty()) {
                continue;
            }
            int chargedDays = countChargedDays(unfrozen.get(), chargingEnabledOn, frozenOn);
            if (chargedDays > 0) {
                planned.add(new PlannedLateFeePeriod(unfrozen.get(), chargedDays, cutoff.kind()));
            }
        }
        return planned;
    }

    private static Optional<LateFeeAccrualPeriod> trimFrozenEdges(LateFeeAccrualPeriod period, Predicate<LocalDate> frozenOn) {
        LocalDate first = period.firstDay();
        LocalDate last = period.lastDay();
        while (!first.isAfter(last) && frozenOn.test(first)) {
            first = first.plusDays(1);
        }
        while (!last.isBefore(first) && frozenOn.test(last)) {
            last = last.minusDays(1);
        }
        return first.isAfter(last) ? Optional.empty() : Optional.of(new LateFeeAccrualPeriod(first, last));
    }

    private static int countChargedDays(LateFeeAccrualPeriod period, Predicate<LocalDate> chargingEnabledOn, Predicate<LocalDate> frozenOn) {
        int enabled = 0;
        for (LocalDate day = period.firstDay(); !day.isAfter(period.lastDay()); day = day.plusDays(1)) {
            if (chargingEnabledOn.test(day) && !frozenOn.test(day)) {
                enabled++;
            }
        }
        return enabled;
    }
}
