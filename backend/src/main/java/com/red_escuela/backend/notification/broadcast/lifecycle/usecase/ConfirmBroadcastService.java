package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastStatusResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastConfirmer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmBroadcastService implements ConfirmBroadcastUseCase {

    private final BroadcastConfirmer broadcastConfirmer;
    private final CurrentActorFinder currentActorFinder;

    public ConfirmBroadcastService(BroadcastConfirmer broadcastConfirmer, CurrentActorFinder currentActorFinder) {
        this.broadcastConfirmer = broadcastConfirmer;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public BroadcastStatusResponse confirmBroadcast(Long broadcastId) {
        return new BroadcastStatusResponse(broadcastId, broadcastConfirmer.confirmBroadcast(broadcastId, currentActorFinder.currentPersonId()));
    }
}
