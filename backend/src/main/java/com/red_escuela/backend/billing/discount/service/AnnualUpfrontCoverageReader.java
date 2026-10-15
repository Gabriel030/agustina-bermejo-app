package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
public class AnnualUpfrontCoverageReader {
    private final PaymentPlanSelectionRepository selectionRepository;

    public AnnualUpfrontCoverageReader(PaymentPlanSelectionRepository selectionRepository) {
        this.selectionRepository = selectionRepository;
    }

    public boolean annualUpfrontCoversAnyMonthOf(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
        if (from == null || to == null) {
            return false;
        }
        List<PaymentPlanSelection> selections = enrollment.kind() == EnrollmentKind.PROGRAM
                ? selectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(enrollment.id())
                : selectionRepository.findByCourseEnrollmentIdOrderByEffectiveFrom(enrollment.id());
        return selections.stream()
                .filter(selection -> selection.getPaymentPlanOption().getCadence() == PaymentPlanCadence.ANNUAL_UPFRONT && selection.getScopeMonths() != null)
                .anyMatch(selection -> {
                    YearMonth coveredFrom = selection.getEffectiveFrom();
                    YearMonth coveredTo = coveredFrom.plusMonths(selection.getScopeMonths() - 1L);
                    return !coveredFrom.isAfter(to) && !coveredTo.isBefore(from);
                });
    }
}
