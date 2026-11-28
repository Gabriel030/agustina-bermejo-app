package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.events.CertificateInvalidatedEvent;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class RegularEnrollmentCertificateRevoker {

    private final RegularStudentCertificateRepository repository;
    private final AuditTrail auditTrail;
    private final RegularEnrollmentFactsFinder factsFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegularEnrollmentCertificateRevoker(RegularStudentCertificateRepository repository, AuditTrail auditTrail,
                                               RegularEnrollmentFactsFinder factsFinder, ApplicationEventPublisher eventPublisher,
                                               Clock clock) {
        this.repository = repository;
        this.auditTrail = auditTrail;
        this.factsFinder = factsFinder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean hasValidCertificate(RegularityKind kind, Long enrollmentId) {
        return validOf(kind, enrollmentId).isPresent();
    }

    @Transactional
    public void revokeValidCertificate(RegularityKind kind, Long enrollmentId, String reason) {
        validOf(kind, enrollmentId).filter(RegularStudentCertificate::isRevocable).ifPresent(certificate -> {
            certificate.revoke(reason, null, LocalDateTime.now(clock));
            repository.save(certificate);
            auditTrail.record(new AuditRecord(AuditAction.REGULAR_CERTIFICATE_REVOKED, AuditEntityType.CERTIFICATE_DOCUMENT,
                    certificate.getId(), "VALID", "REVOKED", reason));
            Long personId = factsFinder.find(kind, enrollmentId).student().getPerson().getId();
            eventPublisher.publishEvent(CertificateInvalidatedEvent.of(certificate.getId(), CertificateInvalidatedEvent.REGULAR,
                    personId, reason));
        });
    }

    private Optional<RegularStudentCertificate> validOf(RegularityKind kind, Long enrollmentId) {
        return kind == RegularityKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID)
                : repository.findByCourseEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID);
    }
}
