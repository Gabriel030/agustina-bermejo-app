package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class AnnualUpfrontCoverageReader {
    private final PaymentPlanSelectionRepository selectionRepository;

    public AnnualUpfrontCoverageReader(PaymentPlanSelectionRepository selectionRepository) {
        this.selectionRepository = selectionRepository;
    }

    public boolean annualUpfrontCoversAnyMonthOf(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
        if (from == null || to == null || enrollment.kind() == EnrollmentKind.COURSE) {
            return false;
        }
        return selectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(enrollment.id()).stream()
                .filter(selection -> selection.getPaymentPlanOption().getCadence() == PaymentPlanCadence.ANNUAL_UPFRONT && selection.getScopeMonths() != null)
                .anyMatch(selection -> {
                    YearMonth coveredFrom = selection.getEffectiveFrom();
                    return !coveredFrom.isAfter(to) && !lastCoveredMonthOf(selection).isBefore(from);
                });
    }

    private YearMonth lastCoveredMonthOf(PaymentPlanSelection selection) {
        YearMonth scopeEnd = selection.getEffectiveFrom().plusMonths(selection.getScopeMonths() - 1L);
        YearMonth closedAt = selection.getEffectiveTo();
        return closedAt != null && closedAt.isBefore(scopeEnd) ? closedAt : scopeEnd;
    }
}
