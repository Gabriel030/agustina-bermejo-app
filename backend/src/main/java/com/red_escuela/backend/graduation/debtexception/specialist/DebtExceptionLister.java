package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionLister {

    private final DocumentDebtExceptionRepository repository;
    private final DebtExceptionExpirer expirer;
    private final DebtExceptionResponses responses;

    public DebtExceptionLister(DocumentDebtExceptionRepository repository, DebtExceptionExpirer expirer,
                               DebtExceptionResponses responses) {
        this.repository = repository;
        this.expirer = expirer;
        this.responses = responses;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Page<DebtExceptionResponse> list(DebtExceptionStatus status, Long studentId, Pageable pageable) {
        expirer.expireDue();
        Specification<DocumentDebtException> filter = (root, query, builder) -> builder.conjunction();
        if (status != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (studentId != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("studentId"), studentId));
        }
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id")));
        Page<DocumentDebtException> page = repository.findAll(filter, ordered);
        return new PageImpl<>(responses.all(page.getContent()), ordered, page.getTotalElements());
    }
}
