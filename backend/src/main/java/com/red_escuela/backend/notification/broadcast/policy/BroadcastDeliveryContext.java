package com.red_escuela.backend.notification.broadcast.policy;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;

public record BroadcastDeliveryContext(Long personId, ChannelType channel, BroadcastCategory category, ConsentDecision consent) {
}
