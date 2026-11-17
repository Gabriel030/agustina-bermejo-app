package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;

public record UnsubscribeLinkResponse(ConsentChannel channel, BroadcastCategory category) {
}
