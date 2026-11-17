package com.red_escuela.backend.notification.broadcast.domain;

import java.util.Objects;

public record RenderedContent(String subject, String body) {

    public RenderedContent {
        Objects.requireNonNull(body, "body");
    }
}
