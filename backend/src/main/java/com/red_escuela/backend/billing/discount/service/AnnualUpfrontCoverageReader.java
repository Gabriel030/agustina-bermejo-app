package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class AnnualUpfrontCoverageReader {
    private final PaymentPlanSelectionRepository selectionRepository;
    private final StudentDiscountRepository studentDiscountRepository;

    public AnnualUpfrontCoverageReader(PaymentPlanSelectionRepository selectionRepository, StudentDiscountRepository studentDiscountRepository) {
        this.selectionRepository = selectionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
    }

    public boolean annualUpfrontCoversAnyMonthOf(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
        if (from == null || to == null || enrollment.kind() == EnrollmentKind.COURSE) {
            return false;
        }
        return annualUpfrontRequestCoversAnyMonthOf(enrollment, from, to) || confirmedSelectionCoversAnyMonthOf(enrollment, from, to);
    }

    private boolean annualUpfrontRequestCoversAnyMonthOf(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
        return studentDiscountRepository.findPeriodsOfLiveAnnualUpfrontRequests(enrollment.id()).stream()
                .map(period -> YearMonth.from(period.toLocalDate()))
                .anyMatch(period -> !period.isBefore(from) && !period.isAfter(to));
    }

    private boolean confirmedSelectionCoversAnyMonthOf(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
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
