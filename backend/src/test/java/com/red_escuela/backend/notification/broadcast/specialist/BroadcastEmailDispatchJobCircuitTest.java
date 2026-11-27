package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.specialist.SharedDailyEmailQuota;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastEmailDispatchJobCircuitTest {
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final EmailDeliveryChannel channel = mock(EmailDeliveryChannel.class);
    private final BroadcastConsentRecheck consentRecheck = mock(BroadcastConsentRecheck.class);
    private final BroadcastProgressTracker progressTracker = mock(BroadcastProgressTracker.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-03-09T13:00:00Z"), ZoneOffset.UTC);
    private final BroadcastEmailDispatchJob job = new BroadcastEmailDispatchJob(repository, channel, consentRecheck, progressTracker,
            new BroadcastDispatchSettings(300, 100, 100, "00:00", "23:59"), new SharedDailyEmailQuota(repository, clock),
            mock(PlatformTransactionManager.class), clock, new SimpleMeterRegistry());

    private void queued(Long... ids) {
        when(repository.findDispatchableIdsUpTo(eq(DeliveryChannel.EMAIL), eq(DeliveryCategory.BROADCAST), any(), any(), any(), any()))
                .thenReturn(List.of(ids));
    }

    @Test
    void openCircuitCutsTheBatchWithoutTouchingAnyDelivery() {
        queued(1L, 2L, 3L);
        when(channel.isMailCircuitOpen()).thenReturn(true);

        int attempted = job.dispatchPendingBroadcastEmails();

        assertThat(attempted).isZero();
        verify(channel, never()).dispatchDelivery(anyLong());
        verify(consentRecheck, never()).suppressIfConsentWithdrawn(anyLong());
        verify(progressTracker, never()).syncDelivery(anyLong());
    }

    @Test
    void circuitOpeningMidBatchStopsTheRemainingDeliveries() {
        queued(1L, 2L, 3L);
        when(channel.isMailCircuitOpen()).thenReturn(false, true);

        int attempted = job.dispatchPendingBroadcastEmails();

        assertThat(attempted).isEqualTo(1);
        verify(channel).dispatchDelivery(1L);
        verify(channel, never()).dispatchDelivery(2L);
    }

    @Test
    void notOpenCircuitDispatchesTheWholeBatch() {
        queued(1L, 2L, 3L);
        when(channel.isMailCircuitOpen()).thenReturn(false);

        int attempted = job.dispatchPendingBroadcastEmails();

        assertThat(attempted).isEqualTo(3);
        verify(channel).dispatchDelivery(3L);
    }
}
