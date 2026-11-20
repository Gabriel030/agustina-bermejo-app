package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionAlreadyOpenException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CertificateExceptionRequester {

    private static final List<CertificateExceptionStatus> OPEN_STATUSES = List.of(CertificateExceptionStatus.PENDING, CertificateExceptionStatus.APPROVED);

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
        if (hasOpenException(command)) {
            throw new CertificateExceptionAlreadyOpenException();
        }
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(command.kind(), command.enrollmentId(),
                facts.student().getId(), command.organismKind(), command.organismReference(), command.reason(),
                currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        try {
            return CertificateExceptionResponses.of(repository.saveAndFlush(approval));
        } catch (DataIntegrityViolationException failure) {
            throw new CertificateExceptionAlreadyOpenException();
        }
    }

    private boolean hasOpenException(RequestCertificateExceptionCommand command) {
        return command.kind() == RegularityKind.PROGRAM
                ? repository.existsByProgramEnrollmentIdAndStatusIn(command.enrollmentId(), OPEN_STATUSES)
                : repository.existsByCourseEnrollmentIdAndStatusIn(command.enrollmentId(), OPEN_STATUSES);
    }
}
