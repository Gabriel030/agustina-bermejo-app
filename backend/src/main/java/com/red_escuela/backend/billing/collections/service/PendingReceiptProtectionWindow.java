package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PendingReceiptProtectionWindow {
    private final BillingConfigurationReader configurationReader;
    private final BusinessDayCalendar businessDayCalendar;

    public PendingReceiptProtectionWindow(BillingConfigurationReader configurationReader, BusinessDayCalendar businessDayCalendar) {
        this.configurationReader = configurationReader;
        this.businessDayCalendar = businessDayCalendar;
    }

    public LocalDate earliestProtectedUploadDateAsOf(LocalDate reference) {
        int cap = configurationReader.findLatePaymentSettingsEffectiveOn(reference)
                .map(LatePaymentTerms::pendingReceiptProtectionBusinessDays)
                .orElse(LatePaymentTerms.DEFAULT_PENDING_RECEIPT_PROTECTION_BUSINESS_DAYS);
        return businessDayCalendar.nthBusinessDayOnOrBefore(reference, cap + 1);
    }
}
