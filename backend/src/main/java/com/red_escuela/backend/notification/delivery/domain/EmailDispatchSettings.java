package com.red_escuela.backend.notification.delivery.domain;

import java.util.Objects;

public record EmailDispatchSettings(EmailDispatchWindow window, int dailyCap) {

    public EmailDispatchSettings {
        Objects.requireNonNull(window, "window");
        if (dailyCap < 0) {
            throw new IllegalArgumentException("The daily cap cannot be negative");
        }
    }
}
