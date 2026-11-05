package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.repository.LiveMonthlyInstallmentPeriodRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Component
public class NextMonthlyInstallmentPeriodQuery {
    private final LiveMonthlyInstallmentPeriodRepository repository;

    public NextMonthlyInstallmentPeriodQuery(LiveMonthlyInstallmentPeriodRepository repository) {
        this.repository = repository;
    }

    public YearMonth findNextMonthlyInstallmentPeriod(EnrollmentReference enrollment, LocalDate today) {
        Set<YearMonth> emitted = new HashSet<>(enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findLivePeriodsOfProgramEnrollment(enrollment.id())
                : repository.findLivePeriodsOfCourseEnrollment(enrollment.id()));
        YearMonth candidate = YearMonth.from(today);
        while (emitted.contains(candidate)) {
            candidate = candidate.plusMonths(1);
        }
        return candidate;
    }
}
