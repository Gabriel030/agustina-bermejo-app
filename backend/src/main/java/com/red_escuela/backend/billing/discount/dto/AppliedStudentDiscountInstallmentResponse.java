package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import org.springframework.lang.Nullable;

import java.time.YearMonth;

public record AppliedStudentDiscountInstallmentResponse(YearMonth period, @Nullable Integer installmentNumber, @Nullable Integer installmentsTotal) {

    public static AppliedStudentDiscountInstallmentResponse from(AppliedStudentDiscountInstallment installment) {
        return new AppliedStudentDiscountInstallmentResponse(installment.period(), installment.installmentNumber(), installment.installmentsTotal());
    }
}
