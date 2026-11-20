package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.exception.ChannelNotEnabledException;
import com.red_escuela.backend.platform.api.WhatsAppApiAvailability;
import org.springframework.stereotype.Component;

@Component
public class BroadcastChannelGate {

    private final WhatsAppApiAvailability whatsAppApiAvailability;

    public BroadcastChannelGate(WhatsAppApiAvailability whatsAppApiAvailability) {
        this.whatsAppApiAvailability = whatsAppApiAvailability;
    }

    public boolean isEnabled(ChannelType channel) {
        return channel != ChannelType.WHATSAPP_API || whatsAppApiAvailability.isEnabled();
    }

    public void requireEnabled(ChannelType channel) {
        if (!isEnabled(channel)) {
            throw new ChannelNotEnabledException(channel);
        }
    }
}
