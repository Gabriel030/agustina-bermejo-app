package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountPage;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountSearch;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountSpecifications;
import com.red_escuela.backend.billing.discount.service.LabeledStudentDiscountReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class SearchStudentDiscountsService implements SearchStudentDiscountsUseCase {
    public static final int MAX_PAGE_SIZE = 100;

    private final StudentDiscountRepository repository;
    private final LabeledStudentDiscountReader labeledReader;

    public SearchStudentDiscountsService(StudentDiscountRepository repository, LabeledStudentDiscountReader labeledReader) {
        this.repository = repository;
        this.labeledReader = labeledReader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public StudentDiscountPage searchStudentDiscounts(StudentDiscountSearch search, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Page<StudentDiscount> found = repository.findAll(StudentDiscountSpecifications.matching(search), PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "id")));
        return new StudentDiscountPage(labeledReader.readLabeled(found.getContent(), true), boundedPage, boundedSize, found.getTotalElements());
    }
}
