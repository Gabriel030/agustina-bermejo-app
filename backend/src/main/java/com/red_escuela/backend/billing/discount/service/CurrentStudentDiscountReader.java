package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CurrentStudentDiscountReader {
    private final StudentDiscountRepository repository;
    private final LabeledStudentDiscountReader labeledReader;
    private final CurrentStudentService currentStudentService;

    public CurrentStudentDiscountReader(StudentDiscountRepository repository, LabeledStudentDiscountReader labeledReader, CurrentStudentService currentStudentService) {
        this.repository = repository;
        this.labeledReader = labeledReader;
        this.currentStudentService = currentStudentService;
    }

    public List<LabeledStudentDiscount> readDiscountsOfCurrentStudent() {
        return labeledReader.readLabeled(repository.findByStudentIdOrderByIdDesc(currentStudentService.resolveCurrentStudent().getId()), false);
    }
}
