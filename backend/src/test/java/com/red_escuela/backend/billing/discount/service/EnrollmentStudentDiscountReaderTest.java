package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-238")
@Tag("D-284")
class EnrollmentStudentDiscountReaderTest {

    private static final Set<StudentDiscountStatus> APPLICABLE = Set.of(StudentDiscountStatus.ACTIVE, StudentDiscountStatus.REVOKED);

    private final StudentDiscountRepository repository = mock(StudentDiscountRepository.class);
    private final EnrollmentDiscountLock enrollmentLock = mock(EnrollmentDiscountLock.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final EnrollmentStudentDiscountReader reader = new EnrollmentStudentDiscountReader(repository, enrollmentLock, entityManager);
    private final StudentDiscount first = mock(StudentDiscount.class);
    private final StudentDiscount second = mock(StudentDiscount.class);

    @Test
    void theSnapshotOfAProgramEnrollmentIsReadWithoutLockAndDetachedFromThePersistenceContext() {
        when(repository.findByProgramEnrollmentIdAndStatusInOrderByIdAsc(10L, APPLICABLE)).thenReturn(List.of(first, second));

        List<StudentDiscount> snapshot = reader.readApplicableDiscountsAsDetachedSnapshot(EnrollmentReference.ofProgram(10L));

        assertThat(snapshot).containsExactly(first, second);
        verify(entityManager).detach(first);
        verify(entityManager).detach(second);
        verify(enrollmentLock, never()).lockDiscountsOf(EnrollmentReference.ofProgram(10L));
    }

    @Test
    void theSnapshotOfACourseEnrollmentUsesTheCourseQuery() {
        when(repository.findByCourseEnrollmentIdAndStatusInOrderByIdAsc(10L, APPLICABLE)).thenReturn(List.of(first));

        assertThat(reader.readApplicableDiscountsAsDetachedSnapshot(EnrollmentReference.ofCourse(10L))).containsExactly(first);
        verify(entityManager).detach(first);
    }

    @Test
    void theLockedReadTakesTheEnrollmentLockBeforeReadingTheProgramDiscountsForUpdate() {
        when(repository.findByProgramEnrollmentIdAndStatusInForUpdate(10L, APPLICABLE)).thenReturn(List.of(first));

        List<StudentDiscount> locked = reader.lockEnrollmentAndReadApplicableDiscounts(EnrollmentReference.ofProgram(10L));

        assertThat(locked).containsExactly(first);
        InOrder order = inOrder(enrollmentLock, repository);
        order.verify(enrollmentLock).lockDiscountsOf(EnrollmentReference.ofProgram(10L));
        order.verify(repository).findByProgramEnrollmentIdAndStatusInForUpdate(10L, APPLICABLE);
        verify(entityManager, never()).detach(first);
    }

    @Test
    void theLockedReadOfACourseEnrollmentUsesTheCourseQueryForUpdate() {
        when(repository.findByCourseEnrollmentIdAndStatusInForUpdate(10L, APPLICABLE)).thenReturn(List.of(second));

        assertThat(reader.lockEnrollmentAndReadApplicableDiscounts(EnrollmentReference.ofCourse(10L))).containsExactly(second);
        verify(enrollmentLock).lockDiscountsOf(EnrollmentReference.ofCourse(10L));
    }
}
