package com.red_escuela.backend.notification.broadcast.usecase.channel;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.dto.BroadcastChannelEntry;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastChannelGate;
import com.red_escuela.backend.shared.UseCase;

import java.util.Arrays;
import java.util.List;

@UseCase
public class ListBroadcastChannelsService implements ListBroadcastChannelsUseCase {

    private final BroadcastChannelGate channelGate;

    public ListBroadcastChannelsService(BroadcastChannelGate channelGate) {
        this.channelGate = channelGate;
    }

    @Override
    public List<BroadcastChannelEntry> listBroadcastChannels() {
        return Arrays.stream(ChannelType.values())
                .map(channel -> new BroadcastChannelEntry(channel, channelGate.isEnabled(channel)))
                .toList();
    }
}
