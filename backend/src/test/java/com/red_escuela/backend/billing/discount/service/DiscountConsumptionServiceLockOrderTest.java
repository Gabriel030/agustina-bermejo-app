package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountEnrollmentIds;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-457")
class DiscountConsumptionServiceLockOrderTest {
    private final StudentDiscountRepository discounts = mock(StudentDiscountRepository.class);
    private final EnrollmentDiscountLock lock = mock(EnrollmentDiscountLock.class);
    private final DiscountConsumptionService service = new DiscountConsumptionService(mock(AppliedStudentDiscountRepository.class), discounts, lock, Clock.systemUTC());

    @Test
    void theEnrollmentsOfSeveralInvoicesAreReadInOneQueryAndLockedOnceEachInKindAndIdOrder() {
        when(discounts.findEnrollmentsOfDiscountsLiveOnInvoices(List.of(1L, 2L))).thenReturn(List.of(
                new DiscountEnrollmentIds(9L, null), new DiscountEnrollmentIds(null, 4L), new DiscountEnrollmentIds(3L, 4L), new DiscountEnrollmentIds(9L, null)));

        service.lockEnrollmentsOfDiscountsAppliedOnInvoices(List.of(1L, 2L));

        InOrder order = inOrder(lock);
        order.verify(lock).lockDiscountsOf(EnrollmentReference.ofProgram(3L));
        order.verify(lock).lockDiscountsOf(EnrollmentReference.ofProgram(9L));
        order.verify(lock).lockDiscountsOf(EnrollmentReference.ofCourse(4L));
        order.verifyNoMoreInteractions();
        verify(discounts, never()).findEnrollmentsOfDiscountsLiveOnInvoice(1L);
    }

    @Test
    void noInvoicesLocksNothingAndDoesNotQuery() {
        service.lockEnrollmentsOfDiscountsAppliedOnInvoices(List.of());

        verifyNoInteractions(discounts, lock);
    }
}
