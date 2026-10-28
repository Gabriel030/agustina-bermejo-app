package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.MonthCloseFacts;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery.InstallmentDueDate;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EnrollmentMonthCloseFactsLoader {

    private final EnrollmentDebtFactsQuery factsQuery;
    private final BillingConfigurationReader configurationReader;

    public EnrollmentMonthCloseFactsLoader(EnrollmentDebtFactsQuery factsQuery, BillingConfigurationReader configurationReader) {
        this.factsQuery = factsQuery;
        this.configurationReader = configurationReader;
    }

    public MonthCloseFacts loadFactsAtCutoff(EnrollmentKind kind, Long enrollmentId, LocalDate cutoff) {
        Map<LocalDate, Integer> graceByDueDate = new HashMap<>();
        List<InstallmentDueDate> overdueAtCutoff = factsQuery.findMonthlyInstallmentsUnpaidAtCutoff(kind, enrollmentId, cutoff).stream()
                .filter(installment -> cutoff.isAfter(installment.dueDate().plusDays(graceByDueDate.computeIfAbsent(installment.dueDate(), this::graceDaysOf))))
                .toList();
        LocalDate oldest = overdueAtCutoff.stream().map(InstallmentDueDate::dueDate).min(LocalDate::compareTo).orElse(null);
        return new MonthCloseFacts(cutoff, overdueAtCutoff.size(), oldest);
    }

    private int graceDaysOf(LocalDate dueDate) {
        return configurationReader.findLatePaymentSettingsEffectiveOn(dueDate).map(LatePaymentTerms::gracePeriodDays).orElse(0);
    }
}
