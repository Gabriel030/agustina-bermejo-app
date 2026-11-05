package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountUsage;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.LiveMonthlyInstallmentPeriodRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StudentDiscountUsageReader {
    private final AppliedStudentDiscountRepository appliedRepository;
    private final LiveMonthlyInstallmentPeriodRepository livePeriodRepository;

    public StudentDiscountUsageReader(AppliedStudentDiscountRepository appliedRepository, LiveMonthlyInstallmentPeriodRepository livePeriodRepository) {
        this.appliedRepository = appliedRepository;
        this.livePeriodRepository = livePeriodRepository;
    }

    public List<StudentDiscountWithUsage> readUsageOf(List<StudentDiscount> discounts) {
        if (discounts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AppliedStudentDiscountInstallment>> applicationsByDiscount = appliedRepository.findLiveApplicationsOf(discounts.stream().map(StudentDiscount::getId).toList())
                .stream().collect(Collectors.groupingBy(AppliedStudentDiscountInstallment::studentDiscountId));
        Map<EnrollmentReference, Set<YearMonth>> livePeriodsByEnrollment = new HashMap<>();
        return discounts.stream()
                .map(discount -> new StudentDiscountWithUsage(discount, StudentDiscountUsage.of(discount, applicationsByDiscount.getOrDefault(discount.getId(), List.of()),
                        livePeriodsByEnrollment.computeIfAbsent(enrollmentOf(discount), this::findLivePeriodsOf))))
                .toList();
    }

    private Set<YearMonth> findLivePeriodsOf(EnrollmentReference enrollment) {
        return new HashSet<>(enrollment.kind() == EnrollmentKind.PROGRAM
                ? livePeriodRepository.findLivePeriodsOfProgramEnrollment(enrollment.id())
                : livePeriodRepository.findLivePeriodsOfCourseEnrollment(enrollment.id()));
    }

    private EnrollmentReference enrollmentOf(StudentDiscount discount) {
        return discount.getProgramEnrollmentId() != null
                ? EnrollmentReference.ofProgram(discount.getProgramEnrollmentId())
                : EnrollmentReference.ofCourse(discount.getCourseEnrollmentId());
    }
}
