package com.red_escuela.backend.billing.pricing.service;

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
                .filter(row -> month.isAfter(((YearMonth) row[1]).plusMonths((Integer) row[2] - 1L)))
                .forEach(row -> paying.add((Long) row[0]));
        return paying.size();
    }

    @Transactional(readOnly = true)
    public long countCourseEnrollmentsPayingMonthlyInstallments(Long courseId, YearMonth month) {
        return selectionRepository.findCourseEnrollmentIdsPayingMonthlyInstallments(courseId, month).size();
    }
}
