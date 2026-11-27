package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.InstallmentBusinessDays;

import java.time.LocalDate;

public final class MonthlyInstallmentDueDateCalculator {

    private final InstallmentBusinessDays businessDays;

    public MonthlyInstallmentDueDateCalculator() {
        this(InstallmentBusinessDays.WEEKDAYS_ONLY);
    }

    public MonthlyInstallmentDueDateCalculator(InstallmentBusinessDays businessDays) {
        this.businessDays = businessDays;
    }

    public LocalDate calculateInstallmentDueDate(LocalDate issueDate, int dueDays) {
        LocalDate dueDate = issueDate.plusDays(dueDays);
        while (!businessDays.isBusinessDay(dueDate)) {
            dueDate = dueDate.plusDays(1);
        }
        return dueDate;
    }
}
