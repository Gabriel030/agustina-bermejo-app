package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-155")
class AnnualUpfrontDeadlineExpirerTest {
    private final AnnualUpfrontRequestRepository repository = mock(AnnualUpfrontRequestRepository.class);
    private final AnnualUpfrontRequestDeadlineDiscarder discarder = mock(AnnualUpfrontRequestDeadlineDiscarder.class);
    private final MutableClock clock = MutableClock.INSTANCE;
    private final AnnualUpfrontDeadlineExpirer expirer = new AnnualUpfrontDeadlineExpirer(repository, discarder, clock);

    @Test
    void aRequestThatFailsToDiscardDoesNotStopTheOthersAndTheRunIsAPartialFailure() {
        LocalDateTime now = LocalDateTime.of(2027, 3, 4, 10, 1);
        clock.setDateTime(now);
        when(repository.findIdsAwaitingPaymentWithDeadlineBefore(now)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(discarder.discardRequestIfDeadlineExpiredWithoutPayment(1L)).thenReturn(true);
        when(discarder.discardRequestIfDeadlineExpiredWithoutPayment(2L)).thenThrow(new IllegalStateException("fallo inyectado"));
        when(discarder.discardRequestIfDeadlineExpiredWithoutPayment(3L)).thenReturn(false);
        when(discarder.discardRequestIfDeadlineExpiredWithoutPayment(4L)).thenReturn(true);

        AnnualUpfrontExpiryResult result = expirer.expireRequestsPastDeadline();

        assertThat(result.expired()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failedRequestIds()).containsExactly(2L);
        assertThat(result.isPartialFailure()).isTrue();
        verify(discarder).discardRequestIfDeadlineExpiredWithoutPayment(eq(4L));
    }

    @Test
    void aRunWithoutFailuresIsNotAPartialFailure() {
        LocalDateTime now = LocalDateTime.of(2027, 3, 4, 10, 1);
        clock.setDateTime(now);
        when(repository.findIdsAwaitingPaymentWithDeadlineBefore(any())).thenReturn(List.of());

        assertThat(expirer.expireRequestsPastDeadline().isPartialFailure()).isFalse();
    }
}
