package com.red_escuela.backend.billing.api;

import java.time.Instant;
import java.util.Objects;

public record PaymentArrival(Long personId, Instant arrivedAt) {

    public PaymentArrival {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
    }
}
