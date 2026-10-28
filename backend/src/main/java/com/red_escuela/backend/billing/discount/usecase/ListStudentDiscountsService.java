package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.LabeledStudentDiscountReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListStudentDiscountsService implements ListStudentDiscountsUseCase {
    private final StudentDiscountRepository repository;
    private final LabeledStudentDiscountReader labeledReader;

    public ListStudentDiscountsService(StudentDiscountRepository repository, LabeledStudentDiscountReader labeledReader) {
        this.repository = repository;
        this.labeledReader = labeledReader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<LabeledStudentDiscount> listStudentDiscounts(EnrollmentReference enrollment) {
        return labeledReader.readLabeled(enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdOrderByIdDesc(enrollment.id())
                : repository.findByCourseEnrollmentIdOrderByIdDesc(enrollment.id()), true);
    }
}
