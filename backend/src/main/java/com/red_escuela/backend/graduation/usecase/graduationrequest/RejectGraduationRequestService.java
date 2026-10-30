package com.red_escuela.backend.graduation.usecase.graduationrequest;

import java.time.Clock;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RejectGraduationRequestService implements RejectGraduationRequestUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationRequestFinder graduationRequestFinder;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RejectGraduationRequestService(GraduationRequestRepository graduationRequestRepository,
                                           GraduationRequestFinder graduationRequestFinder,
                                           CurrentPersonService currentPersonService,
                                           ApplicationEventPublisher eventPublisher,
                                           Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationRequestFinder = graduationRequestFinder;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GraduationRequest reject(RejectGraduationRequestCommand command) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(command.graduationRequestId());
        if (request.getStatus() == GraduationRequestStatus.APPROVED || request.getStatus() == GraduationRequestStatus.REJECTED) {
            throw new InvalidGraduationRequestStateException(command.graduationRequestId());
        }

        request.setStatus(GraduationRequestStatus.REJECTED);
        request.setDecidedBy(currentPersonService.resolveCurrentPerson());
        request.setDecidedAt(LocalDateTime.now(clock));
        request.setRejectionReason(command.motivo());
        GraduationRequest saved = graduationRequestRepository.save(request);

        eventPublisher.publishEvent(GraduationRequestRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
