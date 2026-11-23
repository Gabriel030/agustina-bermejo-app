package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveParameters;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.exception.InvitationPersonAlreadyInvitedException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CreateInvitationWaveTransaction {

    private static final String OPEN_INVITATION_INDEX = "ux_invitation_wave_recipient_open_person";
    private static final int LOOKUP_CHUNK_SIZE = 500;
    private static final List<InvitationRecipientStatus> OPEN_STATUSES = List.of(InvitationRecipientStatus.PENDING, InvitationRecipientStatus.QUEUED);

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationWaveResponseFactory responseFactory;
    private final Clock clock;

    public CreateInvitationWaveTransaction(InvitationWaveRepository waveRepository,
                                           InvitationWaveRecipientRepository recipientRepository,
                                           InvitationWaveResponseFactory responseFactory, Clock clock) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.responseFactory = responseFactory;
        this.clock = clock;
    }

    @Transactional
    public InvitationWaveResponse createInvitationWave(InvitationWaveParameters parameters, Long importBatchId,
                                                       Long createdByPersonId, List<ImportedAccount> candidates) {
        InvitationWave wave = waveRepository.saveAndFlush(InvitationWave.builder()
                .name(parameters.name())
                .status(InvitationWaveStatus.DRAFT)
                .batchSize(parameters.batchSize())
                .intervalMinutes(parameters.intervalMinutes())
                .dailyCap(parameters.dailyCap())
                .importBatchId(importBatchId)
                .createdByPersonId(createdByPersonId)
                .createdAt(Instant.now(clock))
                .build());
        Set<Long> invitedElsewhere = findPersonIdsInvitedElsewhere(candidates);
        List<InvitationWaveRecipient> recipients = candidates.stream()
                .map(account -> toRecipient(wave.getId(), account, invitedElsewhere))
                .toList();
        saveRecipients(recipients);
        return responseFactory.createWaveResponse(wave);
    }

    private void saveRecipients(List<InvitationWaveRecipient> recipients) {
        try {
            recipientRepository.saveAllAndFlush(recipients);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause().getMessage().contains(OPEN_INVITATION_INDEX)) {
                throw new InvitationPersonAlreadyInvitedException();
            }
            throw exception;
        }
    }

    private Set<Long> findPersonIdsInvitedElsewhere(List<ImportedAccount> candidates) {
        List<Long> personIds = candidates.stream().filter(account -> !account.withoutEmail()).map(ImportedAccount::personId).toList();
        Set<Long> invited = new HashSet<>();
        for (int from = 0; from < personIds.size(); from += LOOKUP_CHUNK_SIZE) {
            List<Long> chunk = personIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, personIds.size()));
            invited.addAll(recipientRepository.findPersonIdsWithOpenInvitation(chunk, OPEN_STATUSES));
        }
        return invited;
    }

    private InvitationWaveRecipient toRecipient(Long waveId, ImportedAccount account, Set<Long> invitedElsewhere) {
        if (account.withoutEmail()) {
            return InvitationWaveRecipient.excluded(waveId, account.personId(), InvitationExclusionReason.NO_EMAIL);
        }
        if (invitedElsewhere.contains(account.personId())) {
            return InvitationWaveRecipient.excluded(waveId, account.personId(), InvitationExclusionReason.IN_OTHER_WAVE);
        }
        return InvitationWaveRecipient.pending(waveId, account.personId());
    }
}
