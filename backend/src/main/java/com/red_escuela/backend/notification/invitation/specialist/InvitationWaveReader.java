package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.api.PersonContactSummary;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class InvitationWaveReader {

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationWaveResponseFactory responseFactory;
    private final PersonContactFinder personContactFinder;

    public InvitationWaveReader(InvitationWaveRepository waveRepository,
                                InvitationWaveRecipientRepository recipientRepository,
                                InvitationWaveResponseFactory responseFactory,
                                PersonContactFinder personContactFinder) {
        this.personContactFinder = personContactFinder;
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.responseFactory = responseFactory;
    }

    @Transactional(readOnly = true)
    public InvitationWaveResponse readInvitationWave(Long waveId) {
        return waveRepository.findById(waveId).map(responseFactory::createWaveResponse)
                .orElseThrow(() -> new InvitationWaveNotFoundException(waveId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationWaveResponse> readInvitationWaves(int page, int size) {
        Page<InvitationWave> result =
                waveRepository.findAll(pageRequest(page, size, "createdAt", "id", Sort.Direction.DESC));
        return new PageResponse<>(result.map(responseFactory::createWaveResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationWaveRecipientEntry> readInvitationWaveRecipients(Long waveId,
                                                                                   InvitationRecipientStatus status,
                                                                                   int page, int size,
                                                                                   boolean includePersonalData) {
        if (!waveRepository.existsById(waveId)) {
            throw new InvitationWaveNotFoundException(waveId);
        }
        PageRequest pageRequest = pageRequest(page, size, "id", "id", Sort.Direction.ASC);
        Page<InvitationWaveRecipient> result = status == null
                ? recipientRepository.findByWaveId(waveId, pageRequest)
                : recipientRepository.findByWaveIdAndStatus(waveId, status, pageRequest);
        List<InvitationWaveRecipient> recipients = result.getContent();
        Map<Long, Instant> deliveryCreations = findDeliveryCreations(recipients);
        Map<Long, PersonContactSummary> contacts = includePersonalData
                ? personContactFinder.findContactSummariesByPersonIdIn(recipients.stream().map(InvitationWaveRecipient::getPersonId).toList())
                : Map.of();
        List<InvitationWaveRecipientEntry> entries = recipients.stream()
                .map(recipient -> responseFactory.createRecipientEntry(recipient, recipient.getDeliveryId() == null ? null : deliveryCreations.get(recipient.getDeliveryId()),
                        contacts.get(recipient.getPersonId())))
                .toList();
        return new PageResponse<>(entries,
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private Map<Long, Instant> findDeliveryCreations(List<InvitationWaveRecipient> recipients) {
        List<Long> deliveryIds = recipients.stream().map(InvitationWaveRecipient::getDeliveryId).filter(Objects::nonNull).toList();
        if (deliveryIds.isEmpty()) {
            return Map.of();
        }
        return recipientRepository.findDeliveryCreations(deliveryIds).stream()
                .collect(Collectors.toMap(InvitationWaveRecipientRepository.DeliveryCreation::getDeliveryId,
                        InvitationWaveRecipientRepository.DeliveryCreation::getCreatedAt));
    }

    private PageRequest pageRequest(int page, int size, String primary, String secondary, Sort.Direction direction) {
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(direction, primary, secondary));
    }
}
