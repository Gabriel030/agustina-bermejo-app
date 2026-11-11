package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.service.InvoiceQueryService;

import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.api.StudentPaymentsStandingPort;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@Transactional(readOnly = true)
public class StudentPaymentsStandingPortAdapter implements StudentPaymentsStandingPort {

    private final CurrentBillingModePort currentBillingModePort;
    private final InvoiceQueryService invoiceQueryService;

    public StudentPaymentsStandingPortAdapter(
            CurrentBillingModePort currentBillingModePort, InvoiceQueryService invoiceQueryService) {
        this.currentBillingModePort = currentBillingModePort;
        this.invoiceQueryService = invoiceQueryService;
    }

    @Override
    public Map<Long, PaymentsStanding> findPaymentsStanding(Collection<Long> studentIds, LocalDate today) {
        Map<Long, PaymentsStanding> standings = new HashMap<>();
        if (currentBillingModePort.currentMode() != BillingMode.FULL) {
            studentIds.forEach(studentId -> standings.put(studentId, PaymentsStanding.NOT_VERIFIED));
            return standings;
        }
        Set<Long> withDebt = invoiceQueryService.findStudentIdsWithOverdueDebt(studentIds, today);
        studentIds.forEach(studentId ->
                standings.put(studentId, withDebt.contains(studentId) ? PaymentsStanding.NOT_OK : PaymentsStanding.OK));
        return standings;
    }
}
