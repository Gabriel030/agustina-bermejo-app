package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.ProviderReceipt;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProvider;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadFactory;
import com.red_escuela.backend.shared.resilience.BulkheadSaturator;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreakerState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppDeliverySenderBulkheadTest {

    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final PersonContactFinder contactFinder = mock(PersonContactFinder.class);
    private final WhatsAppProvider provider = mock(WhatsAppProvider.class);
    private final NotificationDelivery delivery = mock(NotificationDelivery.class);
    private CircuitBreakerFactory breakers;
    private Bulkhead bulkhead;
    private WhatsAppDeliverySender sender;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.max-concurrent", "1")
                .withProperty("resilience.default.max-wait", "20ms")
                .withProperty("resilience.default.window", "2")
                .withProperty("resilience.default.failure-threshold", "2")
                .withProperty("resilience.default.wait-open", "30s");
        breakers = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry());
        BulkheadFactory bulkheads = new BulkheadFactory(environment, new SimpleMeterRegistry());
        bulkhead = bulkheads.bulkheadNamed(WhatsAppDeliverySender.CIRCUIT_NAME);
        ObjectProvider<WhatsAppProvider> providerSource = mock(ObjectProvider.class);
        when(providerSource.getIfAvailable()).thenReturn(provider);
        sender = new WhatsAppDeliverySender(repository, contactFinder, providerSource, breakers, bulkheads, new ObjectMapper(), Clock.systemUTC());
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(delivery.getStatus()).thenReturn(DeliveryStatus.QUEUED);
        when(delivery.getRecipientPersonId()).thenReturn(5L);
        when(delivery.getTemplateVariables()).thenReturn("[]");
        when(contactFinder.findPhoneNumber(5L)).thenReturn(Optional.of("011 15 1234-5678"));
    }

    @Test
    void aFullBulkheadLeavesTheDeliveryQueuedWithoutConsumingAttemptsNorCountingAsProviderFailure() {
        try (BulkheadSaturator saturated = BulkheadSaturator.occupying(bulkhead, 1)) {
            for (int i = 0; i < 3; i++) {
                sender.sendQueued(1L);
            }
            verify(provider, never()).sendTemplate(anyString(), any(), any(), anyList());
            verify(delivery, never()).recordFailedAttempt(any(), anyInt());
            verify(delivery, never()).markSent(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
            assertThat(breakers.breakerNamed(WhatsAppDeliverySender.CIRCUIT_NAME).state()).isEqualTo(CircuitBreakerState.CLOSED);
        }
        assertThat(bulkhead.availablePermits()).isEqualTo(1);

        when(provider.sendTemplate(anyString(), any(), any(), anyList())).thenReturn(new ProviderReceipt("wamid.1"));
        sender.sendQueued(1L);

        verify(provider).sendTemplate(anyString(), any(), any(), anyList());
        verify(delivery).markSent(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
        assertThat(bulkhead.availablePermits()).isEqualTo(1);
    }
}
