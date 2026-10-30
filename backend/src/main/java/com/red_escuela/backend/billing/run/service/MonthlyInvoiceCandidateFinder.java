package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
public class MonthlyInvoiceCandidateFinder {
    public static final int PAGE_SIZE = 200;

    private final PaymentPlanSelectionRepository selectionRepository;

    public MonthlyInvoiceCandidateFinder(PaymentPlanSelectionRepository selectionRepository) {
        this.selectionRepository = selectionRepository;
    }

    public List<Long> findProgramEnrollmentIdsAfter(YearMonth period, Long lastId, boolean onlyExpiredAnnualPlans) {
        var activeStatuses = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
        if (!onlyExpiredAnnualPlans) {
            return selectionRepository.findProgramEnrollmentIdsWithActivePlan(PaymentPlanCadence.MONTHLY, activeStatuses, period, lastId, PageRequest.of(0, PAGE_SIZE));
        }
        Long cursor = lastId;
        List<Object[]> selections = selectionRepository.findAnnualUpfrontSelectionsOfProgramEnrollments(activeStatuses, period, cursor, PageRequest.of(0, PAGE_SIZE));
        while (!selections.isEmpty()) {
            List<Long> expired = selections.stream()
                    .filter(row -> period.isAfter(((YearMonth) row[1]).plusMonths((Integer) row[2] - 1L)))
                    .map(row -> (Long) row[0])
                    .distinct()
                    .toList();
            if (!expired.isEmpty()) {
                return expired;
            }
            cursor = (Long) selections.get(selections.size() - 1)[0];
            selections = selectionRepository.findAnnualUpfrontSelectionsOfProgramEnrollments(activeStatuses, period, cursor, PageRequest.of(0, PAGE_SIZE));
        }
        return List.of();
    }

    public List<Long> findCourseEnrollmentIdsAfter(YearMonth period, Long lastId) {
        return selectionRepository.findCourseEnrollmentIdsWithActivePlan(PaymentPlanCadence.MONTHLY, EnrollmentStatus.ENROLLED, period, lastId, PageRequest.of(0, PAGE_SIZE));
    }
}
