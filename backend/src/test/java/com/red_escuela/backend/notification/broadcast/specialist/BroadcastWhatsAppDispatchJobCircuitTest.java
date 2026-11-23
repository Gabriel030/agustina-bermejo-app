package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

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

class BroadcastWhatsAppDispatchJobCircuitTest {
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final WhatsAppDeliverySender sender = mock(WhatsAppDeliverySender.class);
    private final BroadcastConsentRecheck consentRecheck = mock(BroadcastConsentRecheck.class);
    private final BroadcastChannelGate channelGate = mock(BroadcastChannelGate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-03-09T13:00:00Z"), ZoneOffset.UTC);
    private final BroadcastWhatsAppDispatchJob job = new BroadcastWhatsAppDispatchJob(repository, sender, consentRecheck,
            new BroadcastDispatchSettings(300, 100, 100, "00:00", "23:59"), new WhatsAppProviderSettings(100), channelGate, clock,
            new SimpleMeterRegistry());

    private void queued(Long... ids) {
        when(channelGate.isEnabled(ChannelType.WHATSAPP_API)).thenReturn(true);
        when(repository.findDispatchableIdsUpTo(eq(DeliveryChannel.WHATSAPP_API), eq(DeliveryCategory.BROADCAST), any(), any(), any(), any()))
                .thenReturn(List.of(ids));
    }

    @Test
    void openCircuitCutsTheBatchWithoutTouchingAnyDelivery() {
        queued(1L, 2L, 3L);
        when(sender.isCircuitOpen()).thenReturn(true);

        int attempted = job.dispatchPendingBroadcastWhatsApp();

        assertThat(attempted).isZero();
        verify(sender, never()).sendQueued(anyLong());
        verify(consentRecheck, never()).suppressIfConsentWithdrawn(anyLong());
    }

    @Test
    void circuitOpeningMidBatchStopsTheRemainingDeliveries() {
        queued(1L, 2L, 3L);
        when(sender.isCircuitOpen()).thenReturn(false, true);

        int attempted = job.dispatchPendingBroadcastWhatsApp();

        assertThat(attempted).isEqualTo(1);
        verify(sender).sendQueued(1L);
        verify(sender, never()).sendQueued(2L);
    }

    @Test
    void notOpenCircuitDispatchesTheWholeBatch() {
        queued(1L, 2L, 3L);
        when(sender.isCircuitOpen()).thenReturn(false);

        int attempted = job.dispatchPendingBroadcastWhatsApp();

        assertThat(attempted).isEqualTo(3);
        verify(sender).sendQueued(3L);
    }
}
