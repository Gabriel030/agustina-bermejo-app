package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastStatusResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastCanceller;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelBroadcastService implements CancelBroadcastUseCase {

    private final BroadcastCanceller broadcastCanceller;
    private final CurrentActorFinder currentActorFinder;

    public CancelBroadcastService(BroadcastCanceller broadcastCanceller, CurrentActorFinder currentActorFinder) {
        this.broadcastCanceller = broadcastCanceller;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public BroadcastStatusResponse cancelBroadcast(Long broadcastId, String reason) {
        return new BroadcastStatusResponse(broadcastId, broadcastCanceller.cancelBroadcast(broadcastId, currentActorFinder.currentPersonId(), reason));
    }
}
