package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Component
public class MonthlyInstallmentPayerCounter {
    private final PaymentPlanSelectionRepository selectionRepository;

    public MonthlyInstallmentPayerCounter(PaymentPlanSelectionRepository selectionRepository) {
        this.selectionRepository = selectionRepository;
    }

    @Transactional(readOnly = true)
    public long countProgramEnrollmentsPayingMonthlyInstallments(Long programId, YearMonth month) {
        Set<Long> paying = new HashSet<>(selectionRepository.findProgramEnrollmentIdsPayingMonthlyInstallments(programId, month));
        selectionRepository.findAnnualUpfrontSelectionsOfProgram(programId, month).stream()
                .filter(row -> new PaymentPlanCoverage(PaymentPlanCadence.ANNUAL_UPFRONT, (YearMonth) row[1], (Integer) row[2]).isAnnualScopeExpiredAt(month))
                .forEach(row -> paying.add((Long) row[0]));
        return paying.size();
    }

    @Transactional(readOnly = true)
    public long countCourseEnrollmentsPayingMonthlyInstallments(Long courseId, YearMonth month) {
        return selectionRepository.findCourseEnrollmentIdsPayingMonthlyInstallments(courseId, month).size();
    }
}
