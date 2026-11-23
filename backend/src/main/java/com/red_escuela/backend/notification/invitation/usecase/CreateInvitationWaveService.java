package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveParameters;
import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.CreateInvitationWaveTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveCandidateScanner;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveSettings;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;

@UseCase
public class CreateInvitationWaveService implements CreateInvitationWaveUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final InvitationWaveSettings settings;
    private final InvitationWaveCandidateScanner candidateScanner;
    private final CreateInvitationWaveTransaction transaction;

    public CreateInvitationWaveService(CurrentActorFinder currentActorFinder, InvitationWaveSettings settings,
                                       InvitationWaveCandidateScanner candidateScanner,
                                       CreateInvitationWaveTransaction transaction) {
        this.currentActorFinder = currentActorFinder;
        this.settings = settings;
        this.candidateScanner = candidateScanner;
        this.transaction = transaction;
    }

    @Override
    public InvitationWaveResponse createInvitationWave(CreateInvitationWaveCommand command) {
        InvitationWaveParameters parameters = new InvitationWaveParameters(command.name(),
                required(command.batchSize(), "batchSize"), required(command.intervalMinutes(), "intervalMinutes"),
                required(command.dailyCap(), "dailyCap")).requireWithinGlobalDailyLimit(settings.globalDailyLimit());
        if (command.importBatchId() == null) {
            throw new RequestFieldRequiredException("importBatchId is required");
        }
        return transaction.createInvitationWave(parameters, command.importBatchId(),
                currentActorFinder.currentPersonId(), candidateScanner.findCandidates(command.importBatchId()));
    }

    private int required(Integer value, String field) {
        if (value == null) {
            throw new RequestFieldRequiredException(field + " is required");
        }
        return value;
    }
}
