package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateRequestNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateRequestNotPendingException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RejectionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.events.RegularCertificateRequestRejectedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RegularCertificateRequestRejecter {

    private final RegularCertificateRequestRepository repository;
    private final RegularEnrollmentFactsFinder factsFinder;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegularCertificateRequestRejecter(RegularCertificateRequestRepository repository, RegularEnrollmentFactsFinder factsFinder,
                                             CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher,
                                             Clock clock) {
        this.repository = repository;
        this.factsFinder = factsFinder;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public RegularCertificateRequestResponse reject(Long requestId, String rawReason) {
        RegularCertificateRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RegularCertificateRequestNotFoundException());
        if (!request.isPending()) {
            throw new RegularCertificateRequestNotPendingException();
        }
        String reason = RegularCertificateReason.required(rawReason, () -> new RejectionReasonInvalidException());
        request.rejectByStaff(reason, currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        repository.save(request);
        RegularEnrollmentFacts facts = factsFinder.find(request.getKind(), request.enrollmentId());
        Long personId = facts.student().getPerson().getId();
        eventPublisher.publishEvent(RegularCertificateRequestRejectedEvent.of(request.getId(), request.getStudentId(), personId, reason));
        return RegularRequestResponses.of(request, facts);
    }
}
