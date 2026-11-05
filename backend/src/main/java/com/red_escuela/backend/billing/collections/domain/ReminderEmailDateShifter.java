package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;

public final class ReminderEmailDateShifter {

    private ReminderEmailDateShifter() {
    }

    public static LocalDate shiftEmailDate(LocalDate noticeDate, LocalDate dueDate, NonBusinessDays nonBusinessDays) {
        if (nonBusinessDays.isBusinessDay(noticeDate)) {
            return noticeDate;
        }
        LocalDate nextBusinessDay = nonBusinessDays.nextBusinessDayAfter(noticeDate);
        boolean mustNotPassTheDueDate = !noticeDate.isAfter(dueDate);
        if (mustNotPassTheDueDate && nextBusinessDay.isAfter(dueDate)) {
            return nonBusinessDays.previousBusinessDayBefore(noticeDate);
        }
        return nextBusinessDay;
    }
}
