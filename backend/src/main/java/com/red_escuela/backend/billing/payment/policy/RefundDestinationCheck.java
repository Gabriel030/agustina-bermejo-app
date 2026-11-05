package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundDestination;

import java.util.List;

public record RefundDestinationCheck(List<RefundDestination> partDestinations) {

    public RefundDestinationCheck {
        partDestinations = List.copyOf(partDestinations);
    }
}
