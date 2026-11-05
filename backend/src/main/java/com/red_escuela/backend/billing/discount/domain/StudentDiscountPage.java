package com.red_escuela.backend.billing.discount.domain;

import java.util.List;

public record StudentDiscountPage(List<LabeledStudentDiscount> items, int page, int size, long totalElements) {
}
