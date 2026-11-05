package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;

import java.util.List;

public interface ListStudentDiscountsOfStudentUseCase {
    List<LabeledStudentDiscount> listStudentDiscountsOfStudent(Long studentId);
}
