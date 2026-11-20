package com.red_escuela.backend.notification.broadcast.domain;

public sealed interface DispatchOutcome permits DispatchOutcome.Queued, DispatchOutcome.ReadyForManualAction, DispatchOutcome.Rejected {

    record Queued(Long deliveryId) implements DispatchOutcome {
    }

    record ReadyForManualAction(String actionUrl) implements DispatchOutcome {
    }

    record Rejected(DenialReason reason) implements DispatchOutcome {
    }
}
