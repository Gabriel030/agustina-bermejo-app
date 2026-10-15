package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class LateFeeWithdrawalCorrectionService {
    private static final Logger log = LoggerFactory.getLogger(LateFeeWithdrawalCorrectionService.class);

    private final LateFeeChargeRepository chargeRepository;
    private final LateFeeCorrectionIssuer issuer;

    public LateFeeWithdrawalCorrectionService(LateFeeChargeRepository chargeRepository, LateFeeCorrectionIssuer issuer) {
        this.chargeRepository = chargeRepository;
        this.issuer = issuer;
    }

    public int correctLateFeesOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId, LocalDate factDate) {
        List<Long> chargeIds = programEnrollmentId != null ? chargeRepository.findChargeIdsOfProgramEnrollment(programEnrollmentId)
                : chargeRepository.findChargeIdsOfCourseEnrollment(courseEnrollmentId);
        int corrected = 0;
        int failed = 0;
        for (Long chargeId : chargeIds) {
            try {
                if (issuer.correctLateFeeChargeInNewTransaction(chargeId, factDate) == LateFeeCorrectionOutcome.CORRECTED) {
                    corrected++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Falló la corrección del recargo del cargo id={}", chargeId, e);
            }
        }
        if (corrected > 0) {
            log.info("Recargos corregidos por baja: programa={}, curso={}, corregidos={}", programEnrollmentId, courseEnrollmentId, corrected);
        }
        if (failed > 0) {
            throw new IllegalStateException("The late fee correction failed for " + failed + " charges");
        }
        return corrected;
    }
}
