package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.events.CertificateExceptionDecidedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionDecider {

    private final CertificateExceptionApprovalRepository repository;
    private final RegularCertificateSettingsFinder settingsFinder;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final CertificateExceptionResponses responses;

    public CertificateExceptionDecider(CertificateExceptionApprovalRepository repository, RegularCertificateSettingsFinder settingsFinder,
                                       CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher, Clock clock,
                                       CertificateExceptionResponses responses) {
        this.repository = repository;
        this.settingsFinder = settingsFinder;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.responses = responses;
    }

    @Transactional
    public CertificateExceptionResponse approve(Long exceptionId, String decisionReason) {
        CertificateExceptionApproval approval = lock(exceptionId);
        approval.approve(currentActorFinder.currentPersonId(), decisionReason, settingsFinder.current().exceptionValidityDays(),
                LocalDateTime.now(clock));
        return decided(approval, true);
    }

    @Transactional
    public CertificateExceptionResponse reject(Long exceptionId, String decisionReason) {
        CertificateExceptionApproval approval = lock(exceptionId);
        approval.reject(currentActorFinder.currentPersonId(), decisionReason, LocalDateTime.now(clock));
        return decided(approval, false);
    }

    @Transactional
    public CertificateExceptionResponse withdraw(Long exceptionId) {
        CertificateExceptionApproval approval = lock(exceptionId);
        if (!approval.getRequestedById().equals(currentActorFinder.currentPersonId())) {
            throw new CertificateExceptionNotFoundException();
        }
        approval.withdraw(currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        return responses.of(repository.saveAndFlush(approval));
    }

    private CertificateExceptionResponse decided(CertificateExceptionApproval approval, boolean approved) {
        CertificateExceptionApproval saved = repository.saveAndFlush(approval);
        eventPublisher.publishEvent(CertificateExceptionDecidedEvent.of(saved.getId(), saved.getStudentId(), saved.getRequestedById(),
                approved, saved.getDecisionReason()));
        return responses.of(saved);
    }

    private CertificateExceptionApproval lock(Long exceptionId) {
        return repository.findByIdForUpdate(exceptionId).orElseThrow(CertificateExceptionNotFoundException::new);
    }
}
