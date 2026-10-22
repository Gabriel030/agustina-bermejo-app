package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class EnrollmentStudentDiscountReader {
    private static final Set<StudentDiscountStatus> APPLICABLE_STATUSES = Set.of(StudentDiscountStatus.ACTIVE, StudentDiscountStatus.REVOKED, StudentDiscountStatus.EXHAUSTED);

    private final StudentDiscountRepository repository;
    private final EnrollmentDiscountLock enrollmentLock;
    private final EntityManager entityManager;

    public EnrollmentStudentDiscountReader(StudentDiscountRepository repository, EnrollmentDiscountLock enrollmentLock, EntityManager entityManager) {
        this.repository = repository;
        this.enrollmentLock = enrollmentLock;
        this.entityManager = entityManager;
    }

    public List<StudentDiscount> readApplicableDiscountsAsDetachedSnapshot(EnrollmentReference enrollment) {
        List<StudentDiscount> discounts = enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatusInOrderByIdAsc(enrollment.id(), APPLICABLE_STATUSES)
                : repository.findByCourseEnrollmentIdAndStatusInOrderByIdAsc(enrollment.id(), APPLICABLE_STATUSES);
        discounts.forEach(entityManager::detach);
        return discounts;
    }

    public List<StudentDiscount> lockEnrollmentAndReadApplicableDiscounts(EnrollmentReference enrollment) {
        enrollmentLock.lockDiscountsOf(enrollment);
        return enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatusInForUpdate(enrollment.id(), APPLICABLE_STATUSES)
                : repository.findByCourseEnrollmentIdAndStatusInForUpdate(enrollment.id(), APPLICABLE_STATUSES);
    }
}
