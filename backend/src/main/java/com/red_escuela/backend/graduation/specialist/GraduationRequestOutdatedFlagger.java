package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.events.GraduationRequestOutdatedEvent;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GraduationRequestOutdatedFlagger {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GraduationRequestOutdatedFlagger(GraduationRequestRepository graduationRequestRepository,
                                            ApplicationEventPublisher eventPublisher) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void flagOutdatedRequests(Long programEnrollmentId) {
        graduationRequestRepository.findByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED)
                .forEach(request -> eventPublisher.publishEvent(GraduationRequestOutdatedEvent.outdatedFor(request)));
    }
}
