package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastConsentPolicy;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.EmailMessageChannel;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailMessageChannelBatchTest {

    private static final BroadcastCategory CATEGORY = BroadcastCategory.PROMOTIONAL;

    private final ContactConsentLedger ledger = mock(ContactConsentLedger.class);
    private final NotificationDeliveryEnqueuer enqueuer = mock(NotificationDeliveryEnqueuer.class);
    private final PolicySet<BroadcastDeliveryContext> policySet = PolicySet.named("BROADCAST_DELIVERY", List.of(new BroadcastConsentPolicy()));
    private final EmailMessageChannel channel = new EmailMessageChannel(mock(PersonContactFinder.class), ledger, policySet, enqueuer,
            Clock.fixed(Instant.parse("2041-07-08T12:00:00Z"), ZoneOffset.UTC));

    private static OutboundMessage message(long personId) {
        return new OutboundMessage(9L, personId, ChannelAddress.ofEmail("p" + personId + "@example.com"), new RenderedContent("Asunto", "Texto"), CATEGORY);
    }

    @Test
    void dispatchAllResolvesConsentInOneBatchAndKeepsTheOrder() {
        when(ledger.isAllowedAll(any(), eq(ChannelType.EMAIL), eq(CATEGORY))).thenReturn(Map.of(
                1L, ConsentDecision.permitted(), 2L, ConsentDecision.denied(DenialReason.OPTED_OUT), 3L, ConsentDecision.permitted()));
        when(enqueuer.enqueueEmailAndFindDeliveryId(any())).thenReturn(Optional.of(101L), Optional.of(103L));

        List<DispatchOutcome> outcomes = channel.dispatchAll(List.of(message(1L), message(2L), message(3L)));

        assertThat(outcomes).containsExactly(new DispatchOutcome.Queued(101L), new DispatchOutcome.Rejected(DenialReason.OPTED_OUT),
                new DispatchOutcome.Queued(103L));
        verify(ledger, times(1)).isAllowedAll(any(), eq(ChannelType.EMAIL), eq(CATEGORY));
        verify(ledger, never()).isAllowed(anyLong(), any(), any());
    }

    @Test
    void dispatchAllWithNoMessagesDoesNotTouchTheLedger() {
        assertThat(channel.dispatchAll(List.of())).isEmpty();

        verify(ledger, never()).isAllowedAll(any(), any(), any());
    }
}
