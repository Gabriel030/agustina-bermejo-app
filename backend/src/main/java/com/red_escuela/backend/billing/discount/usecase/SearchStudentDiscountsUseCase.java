package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountPage;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountSearch;

public interface SearchStudentDiscountsUseCase {
    StudentDiscountPage searchStudentDiscounts(StudentDiscountSearch search, int page, int size);
}
