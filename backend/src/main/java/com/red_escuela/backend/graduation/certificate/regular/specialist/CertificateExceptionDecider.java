package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionDecider {

    private final CertificateExceptionApprovalRepository repository;
    private final RegularCertificateSettingsFinder settingsFinder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CertificateExceptionDecider(CertificateExceptionApprovalRepository repository, RegularCertificateSettingsFinder settingsFinder,
                                       CurrentActorFinder currentActorFinder, Clock clock) {
        this.repository = repository;
        this.settingsFinder = settingsFinder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public CertificateExceptionResponse approve(Long exceptionId, String decisionReason) {
        CertificateExceptionApproval approval = lock(exceptionId);
        approval.approve(currentActorFinder.currentPersonId(), decisionReason, settingsFinder.current().exceptionValidityDays(),
                LocalDateTime.now(clock));
        return CertificateExceptionResponses.of(repository.saveAndFlush(approval));
    }

    @Transactional
    public CertificateExceptionResponse reject(Long exceptionId, String decisionReason) {
        CertificateExceptionApproval approval = lock(exceptionId);
        approval.reject(currentActorFinder.currentPersonId(), decisionReason, LocalDateTime.now(clock));
        return CertificateExceptionResponses.of(repository.saveAndFlush(approval));
    }

    @Transactional
    public CertificateExceptionResponse withdraw(Long exceptionId) {
        CertificateExceptionApproval approval = lock(exceptionId);
        approval.withdraw(currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        return CertificateExceptionResponses.of(repository.saveAndFlush(approval));
    }

    private CertificateExceptionApproval lock(Long exceptionId) {
        return repository.findByIdForUpdate(exceptionId).orElseThrow(CertificateExceptionNotFoundException::new);
    }
}
