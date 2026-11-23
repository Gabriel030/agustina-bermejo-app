package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ValidRegularCertificateProbe {

    private final RegularStudentCertificateRepository repository;
    private final Clock clock;

    public ValidRegularCertificateProbe(RegularStudentCertificateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean existsForProgramEnrollment(Long enrollmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByProgramEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID)
                .filter(certificate -> !certificate.isExpiredAt(now))
                .isPresent();
    }
}
