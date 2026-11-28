package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class RegularEnrollmentCertificateRevoker {

    private final RegularStudentCertificateRepository repository;
    private final Clock clock;

    public RegularEnrollmentCertificateRevoker(RegularStudentCertificateRepository repository, Clock clock) {
        this.repository = repository;
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
        });
    }

    private Optional<RegularStudentCertificate> validOf(RegularityKind kind, Long enrollmentId) {
        return kind == RegularityKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID)
                : repository.findByCourseEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID);
    }
}
