package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.specialist.GraduationDecisionRecorder;
import com.red_escuela.backend.graduation.specialist.GraduationRequestFinder;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectGraduationRequestService implements RejectGraduationRequestUseCase {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public RejectGraduationRequestService(GraduationRequestFinder graduationRequestFinder,
                                           GraduationDecisionRecorder graduationDecisionRecorder,
                                           ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public GraduationRequest reject(RejectGraduationRequestCommand command) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(command.graduationRequestId());
        if (request.getStatus() == GraduationRequestStatus.APPROVED || request.getStatus() == GraduationRequestStatus.REJECTED) {
            throw new InvalidGraduationRequestStateException(command.graduationRequestId());
        }

        GraduationRequest saved = graduationDecisionRecorder.recordRejection(request, command.motivo());

        eventPublisher.publishEvent(GraduationRequestRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
