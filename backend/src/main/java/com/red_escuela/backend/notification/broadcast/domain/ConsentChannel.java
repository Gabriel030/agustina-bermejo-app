package com.red_escuela.backend.notification.broadcast.domain;

import java.util.List;

public enum ConsentChannel {
    EMAIL(ChannelType.EMAIL),
    WHATSAPP(ChannelType.WHATSAPP_LINK, ChannelType.WHATSAPP_API);

    private final List<ChannelType> storageChannels;

    ConsentChannel(ChannelType... storageChannels) {
        this.storageChannels = List.of(storageChannels);
    }

    public List<ChannelType> storageChannels() {
        return storageChannels;
    }
}
