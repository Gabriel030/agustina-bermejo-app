package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionConsumer {

    private final CertificateExceptionApprovalRepository repository;
    private final Clock clock;

    public CertificateExceptionConsumer(CertificateExceptionApprovalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void consume(Long exceptionId, Long certificateId, Long studentId, RegularityKind kind, Long enrollmentId, Long actorId) {
        CertificateExceptionApproval approval = repository.findByIdForUpdate(exceptionId)
                .orElseThrow(CertificateExceptionNotFoundException::new);
        approval.consume(certificateId, studentId, kind, enrollmentId, actorId, LocalDateTime.now(clock));
        repository.saveAndFlush(approval);
    }
}
