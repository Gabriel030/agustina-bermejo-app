package com.red_escuela.backend.notification.broadcast.domain;

import java.util.Optional;

public final class BroadcastDeliveryKind {

    private static final String PREFIX = "BROADCAST_";

    private BroadcastDeliveryKind() {
    }

    public static String of(BroadcastCategory category) {
        return PREFIX + category.name();
    }

    public static Optional<BroadcastCategory> categoryOf(String kind) {
        if (kind == null || !kind.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(BroadcastCategory.valueOf(kind.substring(PREFIX.length())));
    }
}
