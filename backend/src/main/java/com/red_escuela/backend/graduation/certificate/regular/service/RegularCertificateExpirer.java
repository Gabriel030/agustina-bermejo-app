package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class RegularCertificateExpirer {

    private final RegularStudentCertificateRepository repository;
    private final Clock clock;

    public RegularCertificateExpirer(RegularStudentCertificateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int expireDue() {
        int expired = repository.expireAllBefore(LocalDateTime.now(clock));
        if (expired > 0) {
            log.info("Constancias de regular vencidas: {}", expired);
        }
        return expired;
    }
}
