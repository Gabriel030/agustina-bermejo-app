package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class MissedReminderDaysFinder {
    public static final int MAX_BUSINESS_DAYS_BACK = 3;
    private static final int CALENDAR_DAYS_BACK = 14;

    private final CollectionReminderRunStore runStore;
    private final BusinessDayCalendar businessDayCalendar;

    public MissedReminderDaysFinder(CollectionReminderRunStore runStore, BusinessDayCalendar businessDayCalendar) {
        this.runStore = runStore;
        this.businessDayCalendar = businessDayCalendar;
    }

    public MissedReminderRecovery findRecoveryBefore(LocalDate businessDate) {
        Optional<LocalDate> firstRun = runStore.findEarliestRunDate();
        if (firstRun.isEmpty()) {
            return MissedReminderRecovery.none();
        }
        LocalDate windowStart = windowStartOf(businessDate);
        Set<LocalDate> ranDays = new HashSet<>(runStore.findRunDatesBetween(windowStart, businessDate.minusDays(1)));
        Set<LocalDate> missedDays = new HashSet<>();
        for (LocalDate day = windowStart; day.isBefore(businessDate); day = day.plusDays(1)) {
            if (day.isAfter(firstRun.get()) && !ranDays.contains(day)) {
                missedDays.add(day);
            }
        }
        return MissedReminderRecovery.ofMissedDays(missedDays);
    }

    private LocalDate windowStartOf(LocalDate businessDate) {
        Set<LocalDate> holidays = new HashSet<>();
        for (LocalDate day = businessDate.minusDays(CALENDAR_DAYS_BACK); day.isBefore(businessDate); day = day.plusDays(1)) {
            if (!businessDayCalendar.isBusinessDay(day)) {
                holidays.add(day);
            }
        }
        NonBusinessDays nonBusinessDays = new NonBusinessDays(holidays);
        LocalDate start = businessDate;
        for (int count = 0; count < MAX_BUSINESS_DAYS_BACK; count++) {
            start = nonBusinessDays.previousBusinessDayBefore(start);
        }
        return start;
    }
}
