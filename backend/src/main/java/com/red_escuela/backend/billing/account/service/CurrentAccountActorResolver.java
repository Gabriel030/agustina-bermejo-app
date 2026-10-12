package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountActorResolver {
    private final CurrentPersonService currentPersonService;

    public CurrentAccountActorResolver(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public StatusChangeActor resolveCurrentPersonAsActor() {
        return new StatusChangeActor(currentPersonService.resolveCurrentPerson().getId(), null);
    }
}
