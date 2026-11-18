package com.red_escuela.backend.notification.broadcast.usecase.channel;

import com.red_escuela.backend.notification.broadcast.dto.BroadcastChannelEntry;

import java.util.List;

public interface ListBroadcastChannelsUseCase {
    List<BroadcastChannelEntry> listBroadcastChannels();
}
