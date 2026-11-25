package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ApprovedSubjectsCertificateExpirer {

    private final ApprovedSubjectsCertificateRepository repository;
    private final Clock clock;

    public ApprovedSubjectsCertificateExpirer(ApprovedSubjectsCertificateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int expireDue() {
        int expired = repository.expireAllUntil(LocalDateTime.now(clock));
        if (expired > 0) {
            log.info("Constancias de materias aprobadas vencidas: {}", expired);
        }
        return expired;
    }
}
