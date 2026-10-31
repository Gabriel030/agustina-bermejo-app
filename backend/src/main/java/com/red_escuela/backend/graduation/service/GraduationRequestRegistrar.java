package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.events.GraduationRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GraduationRequestRegistrar {

    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationEligibilityChecker graduationEligibilityChecker;
    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GraduationRequestRegistrar(GraduationRequestRepository graduationRequestRepository,
                                      GraduationEligibilityChecker graduationEligibilityChecker,
                                      ProgramEnrollmentFinder programEnrollmentFinder,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationEligibilityChecker = graduationEligibilityChecker;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public GraduationRequest registerRequest(Long programEnrollmentId) {
        if (!graduationEligibilityChecker.isEligibleToGraduate(programEnrollmentId)) {
            throw new NotEligibleForGraduationException(programEnrollmentId);
        }
        if (!graduationRequestRepository
                .findByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED).isEmpty()) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }
        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(programEnrollmentFinder.find(programEnrollmentId));
        request.setStatus(GraduationRequestStatus.REQUESTED);
        request.setRequestedAt(LocalDateTime.now(clock));
        GraduationRequest saved;
        try {
            saved = graduationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }
        eventPublisher.publishEvent(GraduationRequestedEvent.requestedFor(saved));
        return saved;
    }
}
