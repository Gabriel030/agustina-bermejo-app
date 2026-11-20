package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelCapabilities;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.DeliveryMode;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;

import java.util.List;
import java.util.Optional;

public interface MessageChannel {

    ChannelType type();

    DeliveryMode deliveryMode();

    ChannelCapabilities capabilities();

    Optional<ChannelAddress> resolveAddress(Long personId);

    DispatchOutcome dispatch(OutboundMessage message);

    default List<DispatchOutcome> dispatchAll(List<OutboundMessage> messages) {
        return messages.stream().map(this::dispatch).toList();
    }
}
