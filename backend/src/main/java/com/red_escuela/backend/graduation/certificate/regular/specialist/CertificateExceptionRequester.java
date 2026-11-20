package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionRequester {

    private final CertificateExceptionApprovalRepository repository;
    private final RegularEnrollmentFactsFinder factsFinder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CertificateExceptionRequester(CertificateExceptionApprovalRepository repository, RegularEnrollmentFactsFinder factsFinder,
                                         CurrentActorFinder currentActorFinder, Clock clock) {
        this.repository = repository;
        this.factsFinder = factsFinder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public CertificateExceptionResponse request(RequestCertificateExceptionCommand command) {
        RegularEnrollmentFacts facts = factsFinder.find(command.kind(), command.enrollmentId());
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(command.kind(), command.enrollmentId(),
                facts.student().getId(), command.organismKind(), command.organismReference(), command.reason(),
                currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        return CertificateExceptionResponses.of(repository.saveAndFlush(approval));
    }
}
