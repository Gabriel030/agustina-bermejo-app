package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EnrollmentPaymentStandingOnEnrollmentClosedListenerTest {
    private final EnrollmentPaymentStandingService standingService = mock(EnrollmentPaymentStandingService.class);
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final EnrollmentPaymentStandingOnEnrollmentClosedListener listener = new EnrollmentPaymentStandingOnEnrollmentClosedListener(standingService, effectGuard);
    private final EnrollmentClosedWhileSuspendedEvent event = EnrollmentClosedWhileSuspendedEvent.closedOn(EnrollmentKind.PROGRAM, 77L, LocalDate.of(2028, 1, 1));

    private void guardRunsTheEffect() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(DeduplicableDomainEvent.class), eq("EnrollmentPaymentStandingOnEnrollmentClosedListener"), any(Runnable.class));
    }

    @Test
    @Tag("D-185")
    void aFailureOfTheStandingServiceIsNotSwallowedSoThePublicationStaysPendingAndIsReplayed() {
        guardRunsTheEffect();
        doThrow(new IllegalStateException("database down")).doNothing().when(standingService).recordTermClosedWhileSuspended(EnrollmentKind.PROGRAM, 77L, LocalDate.of(2028, 1, 1));

        assertThatThrownBy(() -> listener.recordWithdrawalForNonpayment(event)).isInstanceOf(IllegalStateException.class).hasMessage("database down");
        listener.recordWithdrawalForNonpayment(event);

        verify(standingService, times(2)).recordTermClosedWhileSuspended(EnrollmentKind.PROGRAM, 77L, LocalDate.of(2028, 1, 1));
    }

    @Test
    @Tag("D-185")
    void theEffectGoesThroughTheProcessedEffectGuardKeyedByTheEventSoAReplayAppliesItOnlyOnce() {
        doNothing().when(standingService).recordTermClosedWhileSuspended(any(), any(), any());
        doAnswer(invocation -> true).when(effectGuard).runEffectOnce(any(DeduplicableDomainEvent.class), any(String.class), any(Runnable.class));

        listener.recordWithdrawalForNonpayment(event);

        verify(effectGuard).runEffectOnce(eq(event), eq("EnrollmentPaymentStandingOnEnrollmentClosedListener"), any(Runnable.class));
        verify(standingService, times(0)).recordTermClosedWhileSuspended(any(), any(), any());
    }

    @Test
    @Tag("D-185")
    void theListenerIsAnApplicationModuleListenerSoItsPublicationIsPersistedAndRetriedOnRestart() throws NoSuchMethodException {
        assertThat(EnrollmentPaymentStandingOnEnrollmentClosedListener.class.getMethod("recordWithdrawalForNonpayment", EnrollmentClosedWhileSuspendedEvent.class)
                .isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }
}
