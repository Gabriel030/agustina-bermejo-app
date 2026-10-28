package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class ScholarshipCoverageReader {
    private final StudentDiscountRepository repository;

    public ScholarshipCoverageReader(StudentDiscountRepository repository) {
        this.repository = repository;
    }

    public boolean activeScholarshipCoversAnyMonthOf(Long programEnrollmentId, YearMonth from, YearMonth to) {
        return repository.findByProgramEnrollmentIdAndStatus(programEnrollmentId, StudentDiscountStatus.ACTIVE).stream()
                .anyMatch(discount -> discount.overlapsRangeOfSameKind(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, from, to));
    }
}
