package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CertificateExceptionExpirer {

    private final CertificateExceptionApprovalRepository repository;
    private final Clock clock;

    public CertificateExceptionExpirer(CertificateExceptionApprovalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CertificateExceptionApproval> due = repository.findByStatusAndValidUntilLessThanEqual(CertificateExceptionStatus.APPROVED, now);
        due.forEach(approval -> approval.expire(now));
        repository.saveAllAndFlush(due);
        return due.size();
    }
}
