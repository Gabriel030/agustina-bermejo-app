package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionDueExpirer {

    private final CertificateExceptionApprovalRepository repository;
    private final Clock clock;

    public CertificateExceptionDueExpirer(CertificateExceptionApprovalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean expireIfDue(Long exceptionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        CertificateExceptionApproval approval = repository.findByIdForUpdate(exceptionId).orElse(null);
        if (approval == null || !approval.isDueToExpireAt(now)) {
            return false;
        }
        approval.expire(now);
        repository.saveAndFlush(approval);
        return true;
    }
}
