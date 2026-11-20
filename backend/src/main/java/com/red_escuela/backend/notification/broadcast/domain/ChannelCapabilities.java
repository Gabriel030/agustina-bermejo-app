package com.red_escuela.backend.notification.broadcast.domain;

public record ChannelCapabilities(boolean supportsHtml, boolean requiresOptIn, boolean supportsTemplates, int maxBodyLength) {

    public ChannelCapabilities {
        if (maxBodyLength < 1) {
            throw new IllegalArgumentException("A channel accepts at least one character per body");
        }
    }
}
