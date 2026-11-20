package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;

public record BroadcastChannelEntry(ChannelType channel, boolean enabled) {
}
