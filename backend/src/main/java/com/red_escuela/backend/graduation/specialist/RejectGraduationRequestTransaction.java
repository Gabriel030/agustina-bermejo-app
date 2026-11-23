package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectGraduationRequestTransaction {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public RejectGraduationRequestTransaction(GraduationRequestFinder graduationRequestFinder,
                                              GraduationDecisionRecorder graduationDecisionRecorder,
                                              ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GraduationRequest reject(Long graduationRequestId, String reason) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (request.getStatus() == GraduationRequestStatus.APPROVED || request.getStatus() == GraduationRequestStatus.REJECTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        GraduationRequest saved = graduationDecisionRecorder.recordRejection(request, reason);

        eventPublisher.publishEvent(GraduationRequestRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
