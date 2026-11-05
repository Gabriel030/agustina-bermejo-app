package com.red_escuela.backend.notification.delivery.domain;

import java.time.LocalTime;
import java.util.Objects;

public record EmailDispatchWindow(LocalTime start, LocalTime end) {

    public static final EmailDispatchWindow STANDARD = new EmailDispatchWindow(LocalTime.of(9, 0), LocalTime.of(11, 0));

    public EmailDispatchWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("The dispatch window must start before it ends");
        }
    }

    public boolean isOpenAt(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
