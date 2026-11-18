package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastStatusResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastConfirmer;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastChannelGate;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmBroadcastService implements ConfirmBroadcastUseCase {

    private final BroadcastConfirmer broadcastConfirmer;
    private final CurrentActorFinder currentActorFinder;
    private final BroadcastRepository broadcastRepository;
    private final BroadcastChannelGate channelGate;

    public ConfirmBroadcastService(BroadcastConfirmer broadcastConfirmer, CurrentActorFinder currentActorFinder,
                                   BroadcastRepository broadcastRepository, BroadcastChannelGate channelGate) {
        this.broadcastConfirmer = broadcastConfirmer;
        this.currentActorFinder = currentActorFinder;
        this.broadcastRepository = broadcastRepository;
        this.channelGate = channelGate;
    }

    @Override
    public BroadcastStatusResponse confirmBroadcast(Long broadcastId) {
        channelGate.requireEnabled(broadcastRepository.findById(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId)).getChannel());
        return new BroadcastStatusResponse(broadcastId, broadcastConfirmer.confirmBroadcast(broadcastId, currentActorFinder.currentPersonId()));
    }
}
