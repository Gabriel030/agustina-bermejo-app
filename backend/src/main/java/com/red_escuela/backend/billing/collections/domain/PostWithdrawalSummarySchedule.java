package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class PostWithdrawalSummarySchedule {

    private PostWithdrawalSummarySchedule() {
    }

    public static LocalDate summaryDayOfMonth(YearMonth month, NonBusinessDays nonBusinessDays) {
        LocalDate firstDay = month.atDay(1);
        return nonBusinessDays.isBusinessDay(firstDay) ? firstDay : nonBusinessDays.nextBusinessDayAfter(firstDay);
    }

    public static boolean isSummaryDay(LocalDate withdrawalEffectiveDate, LocalDate businessDate, int summaryMonths, NonBusinessDays nonBusinessDays) {
        YearMonth currentMonth = YearMonth.from(businessDate);
        long monthsSinceWithdrawal = ChronoUnit.MONTHS.between(YearMonth.from(withdrawalEffectiveDate), currentMonth);
        if (monthsSinceWithdrawal < 1 || monthsSinceWithdrawal > summaryMonths) {
            return false;
        }
        return businessDate.equals(summaryDayOfMonth(currentMonth, nonBusinessDays));
    }
}
