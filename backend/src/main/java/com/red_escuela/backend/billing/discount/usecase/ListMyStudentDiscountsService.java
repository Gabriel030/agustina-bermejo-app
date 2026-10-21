package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.StudentDiscountUsageReader;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListMyStudentDiscountsService implements ListMyStudentDiscountsUseCase {
    private final StudentDiscountRepository repository;
    private final StudentDiscountUsageReader usageReader;
    private final CurrentStudentService currentStudentService;

    public ListMyStudentDiscountsService(StudentDiscountRepository repository, StudentDiscountUsageReader usageReader, CurrentStudentService currentStudentService) {
        this.repository = repository;
        this.usageReader = usageReader;
        this.currentStudentService = currentStudentService;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUMNO')")
    public List<StudentDiscountWithUsage> listMyStudentDiscounts() {
        return usageReader.readUsageOf(repository.findByStudentIdOrderByIdDesc(currentStudentService.resolveCurrentStudent().getId()));
    }
}
