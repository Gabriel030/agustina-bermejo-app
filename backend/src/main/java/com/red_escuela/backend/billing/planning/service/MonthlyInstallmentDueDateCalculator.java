package com.red_escuela.backend.billing.planning.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class MonthlyInstallmentDueDateCalculator {

    public LocalDate calculateInstallmentDueDate(LocalDate issueDate, int dueDays) {
        LocalDate dueDate = issueDate.plusDays(dueDays);
        if (dueDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return dueDate.plusDays(2);
        }
        if (dueDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return dueDate.plusDays(1);
        }
        return dueDate;
    }
}
