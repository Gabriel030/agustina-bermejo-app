package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("c05")
class NotificationDeliveryServiceTest {
    private static final DeliveryEnvelope ENVELOPE = new DeliveryEnvelope("reminder:1:2026-03-09", DeliveryCategory.COLLECTION_REMINDER,
            "BEFORE_DUE_REMINDER", 3, 7L, "Invoice", 11L, LocalDate.of(2026, 3, 9));
    private static final DeliveryContent IN_APP = DeliveryContent.forInApp("Mensaje");
    private static final DeliveryContent EMAIL = DeliveryContent.forEmail("Asunto", "Texto", null);

    private final InAppDeliveryChannel inApp = mock(InAppDeliveryChannel.class);
    private final EmailDeliveryChannel email = mock(EmailDeliveryChannel.class);
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final NotificationDeliveryService service = new NotificationDeliveryService(inApp, email, repository);

    private DeliveryRequest bothChannels() {
        return new DeliveryRequest(ENVELOPE, NotificationType.COLLECTION_REMINDER, IN_APP, EMAIL, true);
    }

    @Test
    void aRequestWithBothChannelsReachesBoth() {
        DeliveryRequest request = bothChannels();

        service.enqueue(request);

        verify(inApp).deliver(request);
        verify(email).enqueue(request);
    }

    @Test
    void aMailOnlyRequestSkipsTheInAppChannel() {
        DeliveryRequest request = new DeliveryRequest(ENVELOPE, null, null, EMAIL, false);

        service.enqueue(request);

        verify(inApp, never()).deliver(any());
        verify(email).enqueue(request);
    }

    @Test
    void anInAppOnlyRequestSkipsTheMailChannel() {
        DeliveryRequest request = new DeliveryRequest(ENVELOPE, NotificationType.COLLECTION_REMINDER, IN_APP, null, false);

        service.enqueue(request);

        verify(inApp).deliver(request);
        verify(email, never()).enqueue(any());
    }

    @Test
    void aUniqueViolationOfAnAlreadyRegisteredDeliveryCountsAsDone() {
        DeliveryRequest request = bothChannels();
        doThrow(new DataIntegrityViolationException("duplicate")).when(email).enqueue(request);
        when(repository.existsByDedupKeyAndChannel(ENVELOPE.dedupKey(), DeliveryChannel.EMAIL)).thenReturn(true);

        assertThatCode(() -> service.enqueue(request)).doesNotThrowAnyException();
        verify(inApp).deliver(request);
    }

    @Test
    void aDataViolationThatIsNotADuplicateIsNotSwallowed() {
        DeliveryRequest request = bothChannels();
        DataIntegrityViolationException foreignKey = new DataIntegrityViolationException("fk");
        doThrow(foreignKey).when(inApp).deliver(request);
        when(repository.existsByDedupKeyAndChannel(ENVELOPE.dedupKey(), DeliveryChannel.IN_APP)).thenReturn(false);

        assertThatThrownBy(() -> service.enqueue(request)).isSameAs(foreignKey);
        verify(email, never()).enqueue(any());
    }

    @Test
    void theDuplicateCheckLooksAtTheChannelThatFailed() {
        DeliveryRequest request = bothChannels();
        doThrow(new DataIntegrityViolationException("duplicate")).when(email).enqueue(request);
        when(repository.existsByDedupKeyAndChannel(ENVELOPE.dedupKey(), DeliveryChannel.IN_APP)).thenReturn(true);
        when(repository.existsByDedupKeyAndChannel(ENVELOPE.dedupKey(), DeliveryChannel.EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> service.enqueue(request)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRequestNeedsAChannelAndItsTypeAndSubject() {
        assertThatThrownBy(() -> new DeliveryRequest(ENVELOPE, null, null, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryRequest(ENVELOPE, null, IN_APP, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryRequest(ENVELOPE, NotificationType.COLLECTION_REMINDER, EMAIL, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryRequest(ENVELOPE, null, null, IN_APP, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(bothChannels().sendsInApp()).isTrue();
        assertThat(bothChannels().sendsEmail()).isTrue();
    }
}
