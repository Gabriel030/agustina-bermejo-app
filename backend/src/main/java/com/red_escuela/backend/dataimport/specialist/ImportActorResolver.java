package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;

@Component
public class ImportActorResolver {
    private final CurrentActorFinder currentActorFinder;

    public ImportActorResolver(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public Long currentPersonId() {
        return currentActorFinder.currentPersonId();
    }
}
