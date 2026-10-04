package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
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
    private final Clock clock;

    public DirectorApprovalRequestDecisionRecorder(DirectorApprovalRequestRepository directorApprovalRequestRepository,
                                                     CurrentPersonService currentPersonService,
                                                     ApplicationEventPublisher eventPublisher,
                                                     Clock clock) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public DirectorApprovalRequest record(DirectorApprovalRequest entity, DirectorApprovalRequestStatus nuevoStatus, String notas) {
        entity.decide(nuevoStatus, currentPersonService.resolveCurrentPerson(), notas, LocalDateTime.now(clock));
        DirectorApprovalRequest saved = directorApprovalRequestRepository.save(entity);

        if (nuevoStatus == DirectorApprovalRequestStatus.APPROVED) {
            eventPublisher.publishEvent(new DirectorApprovalRequestApprovedEvent(saved));
        } else {
            eventPublisher.publishEvent(new DirectorApprovalRequestRejectedEvent(saved));
        }
        return saved;
    }
}
