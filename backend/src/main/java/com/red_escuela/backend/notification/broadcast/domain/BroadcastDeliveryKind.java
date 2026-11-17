package com.red_escuela.backend.notification.broadcast.domain;

public final class BroadcastDeliveryKind {

    private static final String PREFIX = "BROADCAST_";

    private BroadcastDeliveryKind() {
    }

    public static String of(BroadcastCategory category) {
        return PREFIX + category.name();
    }

    public static BroadcastCategory categoryOf(String kind) {
        if (kind == null || !kind.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a broadcast delivery kind: " + kind);
        }
        return BroadcastCategory.valueOf(kind.substring(PREFIX.length()));
    }
}
