package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountPage;

import java.util.List;

public record StudentDiscountPageResponse(List<StudentDiscountResponse> items, int page, int size, long totalElements) {

    public static StudentDiscountPageResponse from(StudentDiscountPage page) {
        return new StudentDiscountPageResponse(page.items().stream().map(item -> StudentDiscountResponse.forStaff(item.discountWithUsage(), item.labels())).toList(),
                page.page(), page.size(), page.totalElements());
    }
}
