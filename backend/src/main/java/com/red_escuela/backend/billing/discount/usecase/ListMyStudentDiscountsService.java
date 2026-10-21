package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.service.CurrentStudentDiscountReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListMyStudentDiscountsService implements ListMyStudentDiscountsUseCase {
    private final CurrentStudentDiscountReader currentStudentDiscountReader;

    public ListMyStudentDiscountsService(CurrentStudentDiscountReader currentStudentDiscountReader) {
        this.currentStudentDiscountReader = currentStudentDiscountReader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUMNO')")
    public List<StudentDiscountWithUsage> listMyStudentDiscounts() {
        return currentStudentDiscountReader.readDiscountsOfCurrentStudent();
    }
}
