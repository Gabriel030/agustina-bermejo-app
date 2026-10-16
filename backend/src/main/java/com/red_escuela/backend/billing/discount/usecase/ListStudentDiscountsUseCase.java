package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;

import java.util.List;

public interface ListStudentDiscountsUseCase {
    List<StudentDiscount> listStudentDiscounts(EnrollmentReference enrollment);
}
