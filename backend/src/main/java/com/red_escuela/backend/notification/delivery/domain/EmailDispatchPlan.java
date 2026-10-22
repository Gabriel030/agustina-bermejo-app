package com.red_escuela.backend.notification.delivery.domain;

import java.util.List;

public record EmailDispatchPlan(List<PlannedDispatch> toSend, List<PlannedDispatch> toDefer, List<DispatchDiscard> toDiscard) {

    public EmailDispatchPlan {
        toSend = List.copyOf(toSend);
        toDefer = List.copyOf(toDefer);
        toDiscard = List.copyOf(toDiscard);
    }
}
