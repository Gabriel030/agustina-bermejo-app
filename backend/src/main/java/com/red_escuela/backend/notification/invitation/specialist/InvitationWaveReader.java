package com.red_escuela.backend.notification.invitation.specialist;

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

@Component
public class InvitationWaveReader {

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationWaveResponseFactory responseFactory;

    public InvitationWaveReader(InvitationWaveRepository waveRepository,
                                InvitationWaveRecipientRepository recipientRepository,
                                InvitationWaveResponseFactory responseFactory) {
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
                                                                                   int page, int size) {
        if (!waveRepository.existsById(waveId)) {
            throw new InvitationWaveNotFoundException(waveId);
        }
        PageRequest pageRequest = pageRequest(page, size, "id", "id", Sort.Direction.ASC);
        Page<InvitationWaveRecipient> result = status == null
                ? recipientRepository.findByWaveId(waveId, pageRequest)
                : recipientRepository.findByWaveIdAndStatus(waveId, status, pageRequest);
        return new PageResponse<>(result.map(responseFactory::createRecipientEntry).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private PageRequest pageRequest(int page, int size, String primary, String secondary, Sort.Direction direction) {
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(direction, primary, secondary));
    }
}
