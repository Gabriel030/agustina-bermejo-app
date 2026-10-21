package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.discount.service.StudentDiscountEnrollmentEndService;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.billing.note.service.LaterChargesCancellationOnWithdrawal;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("D-375")
class WithdrawalCorrectsLateFeesPortTest {

    @Test
    void theWithdrawalCancelsTheLaterChargesBeforeEndingTheDiscountsAndPublishesTheEventWithTheRegistrationDate() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        LaterChargesCancellationOnWithdrawal laterCharges = mock(LaterChargesCancellationOnWithdrawal.class);
        StudentDiscountEnrollmentEndService discountEnd = mock(StudentDiscountEnrollmentEndService.class);
        WithdrawalCorrectsLateFeesPort port = new WithdrawalCorrectsLateFeesPort(publisher, laterCharges, discountEnd);
        WithdrawnEnrollmentFacts facts = WithdrawnEnrollmentFacts.ofCourseEnrollment(7L, 20L, LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 3), WithdrawalCause.VOLUNTARY);

        port.registerWithdrawalForBilling(facts);

        InOrder order = inOrder(discountEnd, laterCharges);
        order.verify(laterCharges).cancelLaterChargesOf(facts);
        order.verify(discountEnd).endActiveDiscountsOf(facts);
        verifyNoMoreInteractions(discountEnd, laterCharges);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        verifyNoMoreInteractions(publisher);
        assertThat(captor.getValue()).isInstanceOfSatisfying(EnrollmentWithdrawnForBillingEvent.class, event -> {
            assertThat(event.studentId()).isEqualTo(7L);
            assertThat(event.courseEnrollmentId()).isEqualTo(20L);
            assertThat(event.programEnrollmentId()).isNull();
            assertThat(event.effectiveDate()).isEqualTo(LocalDate.of(2027, 4, 28));
            assertThat(event.registeredOn()).isEqualTo(LocalDate.of(2027, 5, 3));
            assertThat(event.cause()).isEqualTo("VOLUNTARY");
        });
    }
}
