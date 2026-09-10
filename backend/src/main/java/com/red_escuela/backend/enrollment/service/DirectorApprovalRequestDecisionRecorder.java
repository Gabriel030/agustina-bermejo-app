package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.exception.InvalidDirectorApprovalRequestStateException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.notification.DirectorApprovalRequestRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DirectorApprovalRequestDecisionRecorder {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;

    public DirectorApprovalRequestDecisionRecorder(DirectorApprovalRequestRepository directorApprovalRequestRepository,
                                                     CurrentPersonService currentPersonService,
                                                     ApplicationEventPublisher eventPublisher) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
    }

    public DirectorApprovalRequest record(DirectorApprovalRequest entity, DirectorApprovalRequestStatus nuevoStatus, String notas) {
        if (entity.getStatus() != DirectorApprovalRequestStatus.REQUESTED) {
            throw new InvalidDirectorApprovalRequestStateException(entity.getId());
        }
        entity.setStatus(nuevoStatus);
        entity.setDecidedBy(currentPersonService.resolveCurrentPerson());
        entity.setDecidedAt(LocalDateTime.now());
        entity.setDecisionNotes(notas);
        DirectorApprovalRequest saved = directorApprovalRequestRepository.save(entity);

        if (nuevoStatus == DirectorApprovalRequestStatus.APPROVED) {
            eventPublisher.publishEvent(new DirectorApprovalRequestApprovedEvent(saved, saved.getRequestedBy()));
        } else {
            eventPublisher.publishEvent(new DirectorApprovalRequestRejectedEvent(saved, saved.getRequestedBy()));
        }
        return saved;
    }
}
