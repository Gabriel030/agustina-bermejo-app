package com.red_escuela.backend.billing.discount.domain;

public record LabeledStudentDiscount(StudentDiscountWithUsage discountWithUsage, StudentDiscountLabels labels) {
}
