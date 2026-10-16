package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListStudentDiscountsService implements ListStudentDiscountsUseCase {
    private final StudentDiscountRepository repository;

    public ListStudentDiscountsService(StudentDiscountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<StudentDiscount> listStudentDiscounts(EnrollmentReference enrollment) {
        return enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdOrderByIdDesc(enrollment.id())
                : repository.findByCourseEnrollmentIdOrderByIdDesc(enrollment.id());
    }
}
