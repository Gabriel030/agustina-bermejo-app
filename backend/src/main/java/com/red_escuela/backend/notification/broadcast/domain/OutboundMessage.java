package com.red_escuela.backend.notification.broadcast.domain;

import java.util.Objects;

public record OutboundMessage(Long broadcastId, Long recipientPersonId, ChannelAddress address, RenderedContent content,
                              BroadcastCategory category) {

    public OutboundMessage {
        Objects.requireNonNull(broadcastId, "broadcastId");
        Objects.requireNonNull(recipientPersonId, "recipientPersonId");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(category, "category");
    }
}
