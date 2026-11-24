package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionAlreadyOpenException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.events.CertificateExceptionCreatedByDirectorEvent;
import com.red_escuela.backend.graduation.events.CertificateExceptionRequestedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
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
    private final RegularCertificateSettingsFinder settingsFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;
    private final RegularDebtFactsReader debtFactsReader;
    private final CertificateExceptionResponses responses;

    public CertificateExceptionRequester(CertificateExceptionApprovalRepository repository, RegularEnrollmentFactsFinder factsFinder,
                                         RegularStandingGate standingGate, CurrentActorFinder currentActorFinder, ApplicationEventPublisher eventPublisher,
                                         RegularCertificateSettingsFinder settingsFinder, AuditTrail auditTrail, Clock clock,
                                         RegularDebtFactsReader debtFactsReader, CertificateExceptionResponses responses) {
        this.repository = repository;
        this.factsFinder = factsFinder;
        this.standingGate = standingGate;
        this.currentActorFinder = currentActorFinder;
        this.eventPublisher = eventPublisher;
        this.settingsFinder = settingsFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.debtFactsReader = debtFactsReader;
        this.responses = responses;
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
        recordDebt(approval);
        CertificateExceptionApproval saved = saveRejectingSecondOpenException(approval);
        eventPublisher.publishEvent(CertificateExceptionRequestedEvent.of(saved.getId(), saved.getStudentId(), saved.getRequestedById()));
        return responses.of(saved);
    }

    @Transactional
    public CertificateExceptionResponse createApprovedByDirector(RequestCertificateExceptionCommand command) {
        RegularEnrollmentFacts facts = factsFinder.find(command.kind(), command.enrollmentId());
        standingGate.requireDebtToExcept(facts.student().getId(), command.kind(), command.enrollmentId());
        if (hasOpenException(command)) {
            throw new CertificateExceptionAlreadyOpenException();
        }
        CertificateExceptionApproval approval = CertificateExceptionApproval.createApprovedByDirector(command.kind(), command.enrollmentId(),
                facts.student().getId(), command.organismKind(), command.organismReference(), command.reason(),
                currentActorFinder.currentPersonId(), settingsFinder.current().exceptionValidityDays(), LocalDateTime.now(clock));
        recordDebt(approval);
        CertificateExceptionApproval saved = saveRejectingSecondOpenException(approval);
        auditTrail.record(new AuditRecord(AuditAction.CERTIFICATE_EXCEPTION_DIRECT_CREATED, AuditEntityType.CERTIFICATE_EXCEPTION, saved.getId(),
                null, "APPROVED hasta " + saved.getValidUntil(), saved.getOrganismKind() + " " + saved.getOrganismReference()));
        eventPublisher.publishEvent(CertificateExceptionCreatedByDirectorEvent.of(saved.getId(), saved.getStudentId(), saved.getRequestedById()));
        return responses.of(saved);
    }

    private void recordDebt(CertificateExceptionApproval approval) {
        RegularDebtFacts debt = debtFactsReader.factsOf(List.of(approval.getStudentId())).get(approval.getStudentId());
        if (debt != null) {
            approval.recordDebtAtRequest(debt.totalDue(), debt.overdueInstallments());
        }
    }

    private CertificateExceptionApproval saveRejectingSecondOpenException(CertificateExceptionApproval approval) {
        try {
            return repository.saveAndFlush(approval);
        } catch (DataIntegrityViolationException failure) {
            if (isOpenExceptionViolation(failure)) {
                throw new CertificateExceptionAlreadyOpenException();
            }
            throw failure;
        }
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
