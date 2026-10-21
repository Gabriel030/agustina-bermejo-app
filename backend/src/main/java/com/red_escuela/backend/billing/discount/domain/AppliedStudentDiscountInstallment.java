package com.red_escuela.backend.billing.discount.domain;

import org.springframework.lang.Nullable;

import java.time.YearMonth;

public record AppliedStudentDiscountInstallment(Long studentDiscountId, YearMonth period, @Nullable Integer installmentNumber, @Nullable Integer installmentsTotal) {
}
