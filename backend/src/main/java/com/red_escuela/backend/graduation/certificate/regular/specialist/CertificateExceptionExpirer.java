package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateExceptionExpirer {

    private final CertificateExceptionApprovalRepository repository;
    private final CertificateExceptionDueExpirer dueExpirer;
    private final Clock clock;

    public CertificateExceptionExpirer(CertificateExceptionApprovalRepository repository, CertificateExceptionDueExpirer dueExpirer, Clock clock) {
        this.repository = repository;
        this.dueExpirer = dueExpirer;
        this.clock = clock;
    }

    public int expireDue() {
        int expired = 0;
        for (Long id : repository.findIdsByStatusAndValidUntilLessThanEqual(CertificateExceptionStatus.APPROVED, LocalDateTime.now(clock))) {
            if (dueExpirer.expireIfDue(id)) {
                expired++;
            }
        }
        return expired;
    }
}
