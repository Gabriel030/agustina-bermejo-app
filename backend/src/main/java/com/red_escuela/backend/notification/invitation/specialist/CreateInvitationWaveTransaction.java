package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveParameters;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class CreateInvitationWaveTransaction {

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
        List<InvitationWaveRecipient> recipients = candidates.stream().map(this::toRecipient).toList();
        int excluded = (int) recipients.stream().filter(recipient -> recipient.getStatus() == InvitationRecipientStatus.EXCLUDED).count();
        InvitationWave wave = waveRepository.saveAndFlush(InvitationWave.builder()
                .name(parameters.name())
                .status(InvitationWaveStatus.DRAFT)
                .batchSize(parameters.batchSize())
                .intervalMinutes(parameters.intervalMinutes())
                .dailyCap(parameters.dailyCap())
                .importBatchId(importBatchId)
                .createdByPersonId(createdByPersonId)
                .createdAt(Instant.now(clock))
                .excludedTotal(excluded)
                .build());
        recipients.forEach(recipient -> recipient.setWaveId(wave.getId()));
        recipientRepository.saveAll(recipients);
        return responseFactory.createWaveResponse(wave);
    }

    private InvitationWaveRecipient toRecipient(ImportedAccount account) {
        boolean excluded = account.withoutEmail();
        return InvitationWaveRecipient.builder()
                .personId(account.personId())
                .status(excluded ? InvitationRecipientStatus.EXCLUDED : InvitationRecipientStatus.PENDING)
                .exclusionReason(excluded ? InvitationExclusionReason.NO_EMAIL : null)
                .build();
    }
}
