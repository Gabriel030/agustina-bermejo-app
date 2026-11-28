package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.planning.domain.InstallmentBusinessDays;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BillingCalendarInstallmentBusinessDays implements InstallmentBusinessDays {

    private final BusinessDayCalendar businessDayCalendar;

    public BillingCalendarInstallmentBusinessDays(BusinessDayCalendar businessDayCalendar) {
        this.businessDayCalendar = businessDayCalendar;
    }

    @Override
    public boolean isBusinessDay(LocalDate date) {
        return businessDayCalendar.isBusinessDay(date);
    }
}
