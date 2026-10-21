package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record StudentDiscountUsage(List<AppliedStudentDiscountInstallment> appliedInstallments, List<YearMonth> lostPeriods) {

    public StudentDiscountUsage {
        appliedInstallments = List.copyOf(appliedInstallments);
        lostPeriods = List.copyOf(lostPeriods);
    }

    public static StudentDiscountUsage of(StudentDiscount discount, List<AppliedStudentDiscountInstallment> applications, Set<YearMonth> livePeriodsOfEnrollment) {
        if (discount.getApplicableInvoiceType() != InvoiceType.MONTHLY_INSTALLMENT) {
            return new StudentDiscountUsage(applications, List.of());
        }
        Set<YearMonth> appliedPeriods = applications.stream().map(AppliedStudentDiscountInstallment::period).collect(Collectors.toSet());
        List<YearMonth> lost = livePeriodsOfEnrollment.stream()
                .filter(discount::rangeCoversPeriod)
                .filter(period -> !appliedPeriods.contains(period))
                .sorted()
                .toList();
        return new StudentDiscountUsage(applications, lost);
    }
}
