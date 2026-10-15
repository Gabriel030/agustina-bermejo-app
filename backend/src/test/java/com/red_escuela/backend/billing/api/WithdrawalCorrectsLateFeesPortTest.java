package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("D-375")
class WithdrawalCorrectsLateFeesPortTest {

    @Test
    void theWithdrawalOnlyPublishesTheEventWithTheRegistrationDateAndWritesNothingElse() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        WithdrawalCorrectsLateFeesPort port = new WithdrawalCorrectsLateFeesPort(publisher);

        port.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofCourseEnrollment(7L, 20L, LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 3), WithdrawalCause.VOLUNTARY));

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
