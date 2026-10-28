package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MandatoryNotificationChannels {

    private MandatoryNotificationChannels() {
    }

    public static boolean cannotBeTurnedOff(NotificationEvent event, NotificationChannel channel) {
        return event == NotificationEvent.PAYMENT_DUE_SOON && channel == NotificationChannel.APP;
    }

    public static Map<String, List<String>> channelNamesByEventName() {
        Map<String, List<String>> byEvent = new LinkedHashMap<>();
        for (NotificationEvent event : NotificationEvent.values()) {
            List<String> channels = new ArrayList<>();
            for (NotificationChannel channel : NotificationChannel.values()) {
                if (cannotBeTurnedOff(event, channel)) {
                    channels.add(channel.name());
                }
            }
            byEvent.put(event.name(), channels);
        }
        return byEvent;
    }
}
