package com.red_escuela.backend.notification.broadcast.lifecycle;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.freezer.specialist.RecipientFreezer;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastConfirmer;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastProgressTracker;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.MessageChannel;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastConfirmerCompletionTest {

    private static final Instant NOW = Instant.parse("2041-06-10T12:00:00Z");

    @Test
    void confirmingAsksTheTrackerToCompleteWhenEveryDispatchEndsUpSkipped() {
        BroadcastRepository broadcasts = mock(BroadcastRepository.class);
        BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
        ChannelRegistry registry = mock(ChannelRegistry.class);
        MessageChannel channel = mock(MessageChannel.class);
        BroadcastProgressTracker tracker = mock(BroadcastProgressTracker.class);
        Broadcast broadcast = Broadcast.builder().id(7L).name("Aviso").category(BroadcastCategory.PROMOTIONAL).channel(ChannelType.EMAIL)
                .status(BroadcastStatus.DRAFT).subjectSnapshot("Asunto").bodySnapshot("Cuerpo").createdByPersonId(1L).createdAt(NOW).build();
        BroadcastRecipient recipient = BroadcastRecipient.builder().broadcastId(7L).personId(5L).status(BroadcastRecipientStatus.PENDING).build();
        when(broadcasts.findByIdForUpdate(7L)).thenReturn(Optional.of(broadcast));
        when(recipients.findByBroadcastIdAndStatusIn(eq(7L), any())).thenReturn(List.of(recipient));
        when(registry.channel(ChannelType.EMAIL)).thenReturn(channel);
        when(channel.resolveAddress(anyLong())).thenReturn(Optional.empty());
        BroadcastConfirmer confirmer = new BroadcastConfirmer(broadcasts, recipients, mock(RecipientFreezer.class), registry, tracker,
                mock(AuditTrail.class), Clock.fixed(NOW, ZoneOffset.UTC));

        confirmer.confirmBroadcast(7L, 1L);

        assertThat(recipient.getStatus()).isEqualTo(BroadcastRecipientStatus.SKIPPED);
        verify(tracker).completeWhenNoOpenRecipients(broadcast, NOW);
    }
}
