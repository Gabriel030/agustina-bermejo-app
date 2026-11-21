package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionAlreadyOpenException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.events.CertificateExceptionRequestedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CertificateExceptionRequester {

    private static final List<CertificateExceptionStatus> OPEN_STATUSES = List.of(CertificateExceptionStatus.PENDING, CertificateExceptionStatus.APPROVED);

    static final String OPEN_EXCEPTION_CONSTRAINT = "uq_certificate_exception_open_per_enrollment";

    private final CertificateExceptionApprovalRepository repository;
    private final RegularEnrollmentFactsFinder factsFinder;
    private final RegularStandingGate standingGate;
    private final CurrentActorFinder currentActorFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CertificateExceptionRequester(CertificateExceptionApprovalRepository repository, RegularEnrollmentFactsFinder factsFinder,
                                         RegularStandingGate standingGate, CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.factsFinder = factsFinder;
        this.standingGate = standingGate;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public CertificateExceptionResponse request(RequestCertificateExceptionCommand command) {
        RegularEnrollmentFacts facts = factsFinder.find(command.kind(), command.enrollmentId());
        standingGate.requireDebtToExcept(facts.student().getId(), command.kind(), command.enrollmentId());
        if (hasOpenException(command)) {
            throw new CertificateExceptionAlreadyOpenException();
        }
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(command.kind(), command.enrollmentId(),
                facts.student().getId(), command.organismKind(), command.organismReference(), command.reason(),
                currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        CertificateExceptionApproval saved;
        try {
            saved = repository.saveAndFlush(approval);
        } catch (DataIntegrityViolationException failure) {
            if (isOpenExceptionViolation(failure)) {
                throw new CertificateExceptionAlreadyOpenException();
            }
            throw failure;
        }
        eventPublisher.publishEvent(CertificateExceptionRequestedEvent.of(saved.getId(), saved.getStudentId(), saved.getRequestedById()));
        return CertificateExceptionResponses.of(saved);
    }

    private boolean isOpenExceptionViolation(DataIntegrityViolationException failure) {
        return failure.getCause() instanceof ConstraintViolationException violation
                && OPEN_EXCEPTION_CONSTRAINT.equals(violation.getConstraintName());
    }

    private boolean hasOpenException(RequestCertificateExceptionCommand command) {
        return command.kind() == RegularityKind.PROGRAM
                ? repository.existsByProgramEnrollmentIdAndStatusIn(command.enrollmentId(), OPEN_STATUSES)
                : repository.existsByCourseEnrollmentIdAndStatusIn(command.enrollmentId(), OPEN_STATUSES);
    }
}
