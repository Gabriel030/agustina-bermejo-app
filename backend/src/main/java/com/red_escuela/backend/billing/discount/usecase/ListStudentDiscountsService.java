package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.StudentDiscountUsageReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListStudentDiscountsService implements ListStudentDiscountsUseCase {
    private final StudentDiscountRepository repository;
    private final StudentDiscountUsageReader usageReader;

    public ListStudentDiscountsService(StudentDiscountRepository repository, StudentDiscountUsageReader usageReader) {
        this.repository = repository;
        this.usageReader = usageReader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<StudentDiscountWithUsage> listStudentDiscounts(EnrollmentReference enrollment) {
        return usageReader.readUsageOf(enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdOrderByIdDesc(enrollment.id())
                : repository.findByCourseEnrollmentIdOrderByIdDesc(enrollment.id()));
    }
}
