package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CurrentStudentDiscountReader {
    private final StudentDiscountRepository repository;
    private final StudentDiscountUsageReader usageReader;
    private final CurrentStudentService currentStudentService;

    public CurrentStudentDiscountReader(StudentDiscountRepository repository, StudentDiscountUsageReader usageReader, CurrentStudentService currentStudentService) {
        this.repository = repository;
        this.usageReader = usageReader;
        this.currentStudentService = currentStudentService;
    }

    public List<StudentDiscountWithUsage> readDiscountsOfCurrentStudent() {
        return usageReader.readUsageOf(repository.findByStudentIdOrderByIdDesc(currentStudentService.resolveCurrentStudent().getId()));
    }
}
